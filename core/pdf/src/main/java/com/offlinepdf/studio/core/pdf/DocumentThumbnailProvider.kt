package com.offlinepdf.studio.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-page thumbnails for library cards. Memory LRU + JPEG disk cache; at most two documents are
 * rasterised concurrently so scrolling a long list never starves the reader.
 */
@Singleton
class DocumentThumbnailProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: SafDocumentRepository,
    private val engine: PdfEngine,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val memory = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val gate = Semaphore(2)
    private val dir: File by lazy { File(context.cacheDir, "doc_thumbs").apply { mkdirs() } }

    suspend fun thumbnail(uriString: String, widthPx: Int = 320): Bitmap? {
        val key = "${storage.thumbnailKey(uriString)}_$widthPx"
        memory.get(key)?.let { if (!it.isRecycled) return it }
        return withContext(io) {
            val file = File(dir, "$key.jpg")
            if (file.exists() && file.length() > 0L) {
                BitmapFactory.decodeFile(file.absolutePath)?.let {
                    memory.put(key, it)
                    return@withContext it
                }
            }
            gate.withPermit {
                runCatching {
                    val local = storage.materializeReadOnlyCopy(Uri.parse(uriString))
                    val result = engine.openDocument(local.absolutePath)
                    val handle = (result as? PdfOpenResult.Opened)?.handle ?: run {
                        (result as? PdfOpenResult.PasswordRequired)?.handle?.close()
                        return@runCatching null
                    }
                    try {
                        val bmp = handle.renderThumbnail(0, widthPx)
                        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                        memory.put(key, bmp)
                        bmp
                    } finally {
                        handle.close()
                    }
                }.getOrNull()
            }
        }
    }
}
