package com.aura.audiopro.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AlbumArtCache {
    private val maxMemKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemKb / 8).coerceAtLeast(4096)

    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val colorCache = LruCache<Long, Int>(256)

    fun getBitmap(key: String): Bitmap? = bitmapCache.get(key)
    fun putBitmap(key: String, bitmap: Bitmap) {
        bitmapCache.put(key, bitmap)
    }

    fun getDominantColor(albumId: Long): Int? = colorCache.get(albumId)
    fun putDominantColor(albumId: Long, color: Int) {
        colorCache.put(albumId, color)
    }

    fun clear() {
        bitmapCache.evictAll()
        colorCache.evictAll()
    }
}

suspend fun decodeAlbumBitmap(
    context: Context,
    songUri: Uri,
    albumArtUri: Uri,
    albumId: Long,
    sizePx: Int
): Bitmap? = withContext(Dispatchers.IO) {
    val key = "${albumId}_$sizePx"
    AlbumArtCache.getBitmap(key)?.let { return@withContext it }

    var bitmap: Bitmap? = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            bitmap = context.contentResolver.loadThumbnail(songUri, Size(sizePx, sizePx), null)
        } catch (_: Exception) {
        }
    }
    if (bitmap == null) {
        try {
            context.contentResolver.openInputStream(albumArtUri)?.use { input ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                bitmap = BitmapFactory.decodeStream(input, null, opts)
            }
        } catch (_: Exception) {
        }
    }
    bitmap?.let { bmp ->
        AlbumArtCache.putBitmap(key, bmp)
        if (AlbumArtCache.getDominantColor(albumId) == null) {
            AlbumArtCache.putDominantColor(albumId, computeDominantColor(bmp))
        }
    }
    bitmap
}

fun computeDominantColor(bitmap: Bitmap): Int {
    val tiny = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
    var rTotal = 0L
    var gTotal = 0L
    var bTotal = 0L
    var count = 0L
    for (x in 0 until tiny.width) {
        for (y in 0 until tiny.height) {
            val px = tiny.getPixel(x, y)
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            if (luma in 25..228) {
                rTotal += r
                gTotal += g
                bTotal += b
                count++
            }
        }
    }
    if (count == 0L) return 0xFF1DB954.toInt()
    val rAvg = (rTotal / count).toInt().coerceIn(36, 215)
    val gAvg = (gTotal / count).toInt().coerceIn(36, 215)
    val bAvg = (bTotal / count).toInt().coerceIn(36, 215)
    return (0xFF shl 24) or (rAvg shl 16) or (gAvg shl 8) or bAvg
}
