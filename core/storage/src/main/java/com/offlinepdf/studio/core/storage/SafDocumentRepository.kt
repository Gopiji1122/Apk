package com.offlinepdf.studio.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.database.DocumentDao
import com.offlinepdf.studio.core.database.DocumentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class SafFileMetadata(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long
)

@Singleton
class SafDocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    fun observeAllDocuments(): Flow<List<DocumentEntity>> = documentDao.observeAllDocuments()
    fun observeRecentDocuments(limit: Int = 10): Flow<List<DocumentEntity>> =
        documentDao.observeRecentDocuments(limit)
    fun observeFavorites(): Flow<List<DocumentEntity>> = documentDao.observeFavorites()
    fun observeScannedDocuments(): Flow<List<DocumentEntity>> = documentDao.observeScannedDocuments()

    private fun workingCopyFileFor(uriString: String): File {
        val dir = File(context.filesDir, "pdf_working_copies").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uriString.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return File(dir, "$hash.pdf")
    }

    suspend fun importPickedDocument(uri: Uri, pageCount: Int = 0): DocumentEntity =
        withContext(ioDispatcher) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // OEM file managers (e.g. OnePlus FilePathProvider) grant temporary read access only.
            }

            // Immediately materialize a read-only private copy while the temporary URI grant is active.
            val copiedFile = runCatching { copyUriToWorkingFile(uri) }.getOrNull()
            val meta = runCatching { queryMetadata(uri) }.getOrElse {
                SafFileMetadata(
                    uri = uri,
                    displayName = extractFallbackName(uri),
                    sizeBytes = copiedFile?.length() ?: 0L
                )
            }

            val existing = documentDao.getByUri(uri.toString())
            val resolvedSize = when {
                meta.sizeBytes > 0L -> meta.sizeBytes
                copiedFile != null && copiedFile.length() > 0L -> copiedFile.length()
                else -> existing?.sizeBytes ?: 0L
            }

            val entity = DocumentEntity(
                uriString = uri.toString(),
                displayName = meta.displayName.ifBlank { existing?.displayName ?: extractFallbackName(uri) },
                sizeBytes = resolvedSize,
                pageCount = if (pageCount > 0) pageCount else (existing?.pageCount ?: 0),
                lastOpenedAt = System.currentTimeMillis(),
                lastReadPage = existing?.lastReadPage ?: 0,
                isFavorite = existing?.isFavorite ?: false,
                isScanned = existing?.isScanned ?: false,
                folderName = existing?.folderName ?: "Imported"
            )
            documentDao.upsert(entity)
            entity
        }

    suspend fun queryMetadata(uri: Uri): SafFileMetadata = withContext(ioDispatcher) {
        var name = extractFallbackName(uri)
        var size = 0L
        if (uri.scheme == "file") {
            val f = File(uri.path.orEmpty())
            if (f.exists()) {
                return@withContext SafFileMetadata(uri = uri, displayName = f.name, sizeBytes = f.length())
            }
        }
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        SafFileMetadata(uri = uri, displayName = name, sizeBytes = size)
    }

    /**
     * Returns a local readable PDF file for native MuPDF without modifying the original document.
     * If a temporary OEM file-manager URI grant has expired on a subsequent launch, falls back
     * seamlessly to the previously materialized private copy.
     */
    suspend fun materializeReadOnlyCopy(uri: Uri): File = withContext(ioDispatcher) {
        if (uri.scheme == "file") {
            val direct = File(uri.path.orEmpty())
            if (direct.exists() && direct.canRead()) return@withContext direct
        }

        val target = workingCopyFileFor(uri.toString())
        try {
            copyUriToWorkingFile(uri)
        } catch (se: SecurityException) {
            if (target.exists() && target.length() > 0L) {
                target
            } else {
                throw se
            }
        } catch (t: Throwable) {
            if (target.exists() && target.length() > 0L) {
                target
            } else {
                throw t
            }
        }
    }

    private fun copyUriToWorkingFile(uri: Uri): File {
        val outFile = workingCopyFileFor(uri.toString())
        val tempFile = File(outFile.parentFile, "${outFile.name}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: error("Unable to open PDF stream for URI: $uri")

        if (tempFile.exists() && tempFile.length() > 0L) {
            if (outFile.exists()) outFile.delete()
            if (!tempFile.renameTo(outFile)) {
                tempFile.copyTo(outFile, overwrite = true)
                tempFile.delete()
            }
        }
        return outFile
    }

    private fun extractFallbackName(uri: Uri): String {
        val last = uri.lastPathSegment?.substringAfterLast('/')?.trim()
        return if (!last.isNullOrBlank()) last else "Document.pdf"
    }

    suspend fun updatePageCountAndProgress(uri: String, pageCount: Int, lastReadPage: Int) =
        withContext(ioDispatcher) {
            val existing = documentDao.getByUri(uri)
            if (existing != null) {
                documentDao.upsert(
                    existing.copy(
                        pageCount = pageCount,
                        lastReadPage = lastReadPage,
                        lastOpenedAt = System.currentTimeMillis()
                    )
                )
            }
        }

    suspend fun toggleFavorite(uri: String, isFavorite: Boolean) = withContext(ioDispatcher) {
        documentDao.setFavorite(uri, isFavorite)
    }

    suspend fun renameInLibrary(uri: String, newName: String) = withContext(ioDispatcher) {
        val sanitized = newName.trim().ifEmpty { return@withContext }
        val docFile = runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uri)) }.getOrNull()
        runCatching { docFile?.renameTo(sanitized) }
        documentDao.updateDisplayName(uri, sanitized)
    }

    suspend fun removeFromLibrary(uri: String) = withContext(ioDispatcher) {
        documentDao.deleteByUri(uri)
        workingCopyFileFor(uri).delete()
    }

    suspend fun clearWorkingCache(): Long = withContext(ioDispatcher) {
        val dir1 = File(context.filesDir, "pdf_working_copies")
        val dir2 = File(context.cacheDir, "pdf_working_copies")
        val freed = calculateDirSize(dir1) + calculateDirSize(dir2)
        dir1.deleteRecursively()
        dir2.deleteRecursively()
        freed
    }

    suspend fun getWorkingCacheSizeBytes(): Long = withContext(ioDispatcher) {
        calculateDirSize(File(context.filesDir, "pdf_working_copies")) +
            calculateDirSize(File(context.cacheDir, "pdf_working_copies"))
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun exportPdfToUri(sourceUriString: String, destinationUri: Uri): Boolean =
        withContext(ioDispatcher) {
            runCatching {
                val sourceFile = materializeReadOnlyCopy(Uri.parse(sourceUriString))
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: error("Cannot open destination output stream")
                true
            }.getOrDefault(false)
        }

}
