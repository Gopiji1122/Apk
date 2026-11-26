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

    suspend fun importPickedDocument(uri: Uri, pageCount: Int = 0): DocumentEntity =
        withContext(ioDispatcher) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers grant temporary read access only; we copy to private working cache on open.
            }

            val meta = queryMetadata(uri)
            val existing = documentDao.getByUri(uri.toString())
            val entity = DocumentEntity(
                uriString = uri.toString(),
                displayName = meta.displayName,
                sizeBytes = meta.sizeBytes,
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
        var name = "Document.pdf"
        var size = 0L
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
     * Copies a SAF content URI into an app-private cache file so native MuPDF can memory-map
     * and seek reliably without ever mutating the user's original file.
     */
    suspend fun materializeReadOnlyCopy(uri: Uri): File = withContext(ioDispatcher) {
        val cacheDir = File(context.cacheDir, "pdf_working_copies").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
        val outFile = File(cacheDir, "$hash.pdf")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: error("Unable to open PDF stream for URI: $uri")

        outFile
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
    }

    suspend fun clearWorkingCache(): Long = withContext(ioDispatcher) {
        val cacheDir = File(context.cacheDir, "pdf_working_copies")
        val freed = calculateDirSize(cacheDir)
        cacheDir.deleteRecursively()
        freed
    }

    suspend fun getWorkingCacheSizeBytes(): Long = withContext(ioDispatcher) {
        calculateDirSize(File(context.cacheDir, "pdf_working_copies"))
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
