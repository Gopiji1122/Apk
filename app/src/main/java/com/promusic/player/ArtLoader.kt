package com.promusic.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val misses = HashSet<String>()

    private fun key(song: Song, size: Int): String =
        (if (song.albumId > 0) "a${song.albumId}" else "s${song.id}") + "_" + size

    fun peek(song: Song?, size: Int): Bitmap? =
        if (song == null) null else cache.get(key(song, size))

    suspend fun load(ctx: Context, song: Song, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        val k = key(song, size)
        val hit = cache.get(k)
        if (hit != null) return@withContext hit
        val missed = synchronized(misses) { misses.contains(k) }
        if (missed) return@withContext null

        var bmp: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                bmp = ctx.contentResolver.loadThumbnail(song.uri, Size(size, size), null)
            } catch (_: Throwable) {
            }
        }
        if (bmp == null) bmp = embedded(ctx, song, size)

        val result = bmp
        if (result != null) {
            cache.put(k, result)
        } else {
            synchronized(misses) { misses.add(k) }
        }
        result
    }

    private fun embedded(ctx: Context, song: Song, size: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, song.uri)
            val data = r.embeddedPicture
            if (data == null) {
                null
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            }
        } catch (_: Throwable) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Throwable) {
            }
        }
    }
}

/** Picks a rich, mid-dark colour from an album cover for the player background. */
fun dominantColor(b: Bitmap): Color {
    val n = 12
    val s = Bitmap.createScaledBitmap(b, n, n, true)
    var wr = 0f
    var wg = 0f
    var wb = 0f
    var wt = 0f
    val hsv = FloatArray(3)
    for (x in 0 until n) {
        for (y in 0 until n) {
            val p = s.getPixel(x, y)
            android.graphics.Color.colorToHSV(p, hsv)
            val w = hsv[1] * hsv[2] + 0.02f
            wr += android.graphics.Color.red(p) * w
            wg += android.graphics.Color.green(p) * w
            wb += android.graphics.Color.blue(p) * w
            wt += w
        }
    }
    val r = (wr / wt).toInt().coerceIn(0, 255)
    val g = (wg / wt).toInt().coerceIn(0, 255)
    val bl = (wb / wt).toInt().coerceIn(0, 255)
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.rgb(r, g, bl), out)
    out[1] = out[1].coerceIn(0.30f, 0.80f)
    out[2] = out[2].coerceIn(0.28f, 0.50f)
    return Color(android.graphics.Color.HSVToColor(out))
}
