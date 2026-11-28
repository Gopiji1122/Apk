package com.offlinepdf.studio.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
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

    suspend fun importPickedDocument(
        uri: Uri,
        pageCount: Int = 0,
        materializeCopy: Boolean = true
    ): DocumentEntity =
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
            val copiedFile = if (materializeCopy) {
                runCatching { materializeReadOnlyCopy(uri) }.getOrNull()
            } else {
                null
            }
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
        // Re-opening a large PDF should be instant: skip the copy when the source is unchanged.
        if (target.exists() && target.length() > 0L && isWorkingCopyFresh(uri, target)) {
            return@withContext target
        }
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

    /** Returns (size, lastModified) of the source when the provider exposes both, otherwise null. */
    private fun querySourceStamp(uri: Uri): Pair<Long, Long>? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null, null
        )?.use { c ->
            if (!c.moveToFirst() || c.isNull(0) || c.isNull(1)) return@use null
            val size = c.getLong(0)
            val modified = c.getLong(1)
            if (size > 0L && modified > 0L) size to modified else null
        }
    }.getOrNull()

    private fun isWorkingCopyFresh(uri: Uri, target: File): Boolean {
        val stamp = querySourceStamp(uri) ?: return false
        return target.length() == stamp.first && target.lastModified() == stamp.second
    }

    private fun copyUriToWorkingFile(uri: Uri): File {
        val outFile = workingCopyFileFor(uri.toString())
        val tempFile = File(outFile.parentFile, "${outFile.name}.tmp")
        val stamp = querySourceStamp(uri)
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
            // Remember the source's modified time so the next open can skip the copy.
            if (stamp != null) outFile.setLastModified(stamp.second)
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
        val trimmed = newName.trim().ifEmpty { return@withContext }
        val sanitized = if (trimmed.endsWith(".pdf", ignoreCase = true)) trimmed else "$trimmed.pdf"
        val docFile = runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uri)) }.getOrNull()
        runCatching { docFile?.renameTo(sanitized) }
        documentDao.updateDisplayName(uri, sanitized)
    }

    suspend fun removeFromLibrary(uri: String) = withContext(ioDispatcher) {
        documentDao.deleteByUri(uri)
        workingCopyFileFor(uri).delete()
        File(context.cacheDir, "doc_thumbs").listFiles()
            ?.filter { it.name.startsWith(thumbnailKey(uri)) }
            ?.forEach { it.delete() }
    }

    suspend fun clearWorkingCache(): Long = withContext(ioDispatcher) {
        val dir1 = File(context.filesDir, "pdf_working_copies")
        val dir2 = File(context.cacheDir, "pdf_working_copies")
        val dir3 = File(context.cacheDir, "doc_thumbs")
        val dir4 = File(context.cacheDir, "shared")
        val freed = calculateDirSize(dir1) + calculateDirSize(dir2) + calculateDirSize(dir3) + calculateDirSize(dir4)
        dir1.deleteRecursively()
        dir2.deleteRecursively()
        dir3.deleteRecursively()
        dir4.deleteRecursively()
        freed
    }

    suspend fun getWorkingCacheSizeBytes(): Long = withContext(ioDispatcher) {
        calculateDirSize(File(context.filesDir, "pdf_working_copies")) +
            calculateDirSize(File(context.cacheDir, "pdf_working_copies")) +
            calculateDirSize(File(context.cacheDir, "doc_thumbs")) +
            calculateDirSize(File(context.cacheDir, "shared"))
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun exportPdfToUri(sourceUriString: String, destinationUri: Uri): Boolean =
        withContext(ioDispatcher) {
            runCatching {
                val sourceFile = materializeReadOnlyCopy(Uri.parse(sourceUriString))
                context.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: error("Cannot open destination output stream")
                true
            }.getOrDefault(false)
        }


    suspend fun registerScannedPdf(uri: Uri, fallbackName: String, pageCount: Int): DocumentEntity =
        withContext(ioDispatcher) {
            val base = importPickedDocument(uri, pageCount = pageCount)
            val updated = base.copy(
                displayName = base.displayName.ifBlank { fallbackName },
                pageCount = pageCount,
                isScanned = true,
                folderName = "Scanned"
            )
            documentDao.upsert(updated)
            updated
        }

    /** Stable short key used for on-disk caches (thumbnails) belonging to a document URI. */
    fun thumbnailKey(uriString: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(uriString.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    /**
     * Prepares a shareable `content://` URI served by this app's FileProvider. Third-party apps
     * can never read the original SAF/OEM URI, so we hand out a clean copy named after the document.
     */
    suspend fun prepareShareUri(uriString: String, displayName: String): Uri =
        withContext(ioDispatcher) {
            val source = materializeReadOnlyCopy(Uri.parse(uriString))
            val dir = File(context.cacheDir, "shared").apply {
                deleteRecursively()
                mkdirs()
            }
            val safeName = displayName
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .ifBlank { "Document" }
                .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
            val shared = File(dir, safeName)
            source.copyTo(shared, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
        }
}
