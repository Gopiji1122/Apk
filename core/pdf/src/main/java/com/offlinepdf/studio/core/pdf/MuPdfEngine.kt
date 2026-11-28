package com.offlinepdf.studio.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.LruCache
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Outline
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.offlinepdf.studio.core.common.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Singleton
class MuPdfEngine @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PdfEngine {

    override suspend fun openDocument(localFilePath: String): PdfOpenResult =
        withContext(ioDispatcher) {
            val file = File(localFilePath)
            if (!file.exists() || file.length() == 0L) {
                return@withContext PdfOpenResult.Error("PDF file is missing or empty.")
            }

            try {
                val nativeDoc = Document.openDocument(localFilePath)
                val handle = MuPdfDocumentHandle(nativeDoc)
                if (nativeDoc.needsPassword()) {
                    PdfOpenResult.PasswordRequired(handle)
                } else {
                    PdfOpenResult.Opened(handle, handle.getMetadata())
                }
            } catch (t: Throwable) {
                PdfOpenResult.Error(
                    message = t.localizedMessage ?: "Failed to open PDF document.",
                    cause = t
                )
            }
        }
}

/**
 * Thread-safe wrapper around MuPDF's native [Document].
 * Separates full-resolution page cache from thumbnail cache and always releases
 * native [com.artifex.mupdf.fitz.Page] and [com.artifex.mupdf.fitz.StructuredText] objects.
 */
class MuPdfDocumentHandle(
    private val document: Document
) : PdfDocumentHandle {

    private val lock = Any()
    @Volatile
    private var closed = false

    private val maxPageCacheKb: Int = ((Runtime.getRuntime().maxMemory() / 1024L) / 10L)
        .toInt()
        .coerceIn(8 * 1024, 32 * 1024)

    private val pageCache = object : LruCache<String, Bitmap>(maxPageCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val thumbCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private companion object {
        const val MAX_TILE_SIDE = 2400f
    }

    override val pageCount: Int
        get() = synchronized(lock) {
            if (closed) 0 else document.countPages()
        }

    override val isPasswordProtected: Boolean
        get() = synchronized(lock) {
            !closed && document.needsPassword()
        }

    override fun authenticate(password: String): Boolean = synchronized(lock) {
        if (closed) return false
        document.authenticatePassword(password)
    }

    override fun getMetadata(): PdfMetadata = synchronized(lock) {
        if (closed) return PdfMetadata()
        PdfMetadata(
            title = document.getMetaData(Document.META_INFO_TITLE).orEmpty(),
            author = document.getMetaData(Document.META_INFO_AUTHOR).orEmpty(),
            subject = document.getMetaData(Document.META_INFO_SUBJECT).orEmpty(),
            creator = document.getMetaData(Document.META_INFO_CREATOR).orEmpty(),
            producer = document.getMetaData(Document.META_INFO_PRODUCER).orEmpty(),
            format = document.getMetaData(Document.META_FORMAT).orEmpty().ifBlank { "PDF" },
            encryption = document.getMetaData(Document.META_ENCRYPTION).orEmpty().ifBlank { "None" },
            pageCount = document.countPages()
        )
    }

    override fun getPageSize(pageIndex: Int): PdfPageSize = synchronized(lock) {
        check(!closed) { "Document is closed" }
        val page = document.loadPage(pageIndex)
        try {
            val bounds = page.bounds
            PdfPageSize(
                widthPt = max(1f, bounds.x1 - bounds.x0),
                heightPt = max(1f, bounds.y1 - bounds.y0)
            )
        } finally {
            page.destroy()
        }
    }

    override fun getOutline(): List<PdfOutlineEntry> = synchronized(lock) {
        if (closed) return emptyList()
        val roots = runCatching { document.loadOutline() }.getOrNull() ?: return emptyList()
        val result = mutableListOf<PdfOutlineEntry>()
        flattenOutline(roots, 0, result)
        result
    }

    private fun flattenOutline(
        nodes: Array<Outline>,
        depth: Int,
        out: MutableList<PdfOutlineEntry>
    ) {
        for (node in nodes) {
            val loc = runCatching { document.resolveLink(node) }.getOrNull()
            val pageNum = if (loc != null) document.pageNumberFromLocation(loc) else -1
            if (!node.title.isNullOrBlank() && pageNum >= 0) {
                out.add(PdfOutlineEntry(title = node.title.trim(), pageIndex = pageNum, depth = depth))
            }
            val children = node.down
            if (children != null && children.isNotEmpty() && depth < 5) {
                flattenOutline(children, depth + 1, out)
            }
        }
    }

    override fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        rotationDegrees: Int,
        invertColors: Boolean
    ): Bitmap {
        val safeWidth = targetWidthPx.coerceIn(120, 2600)
        val normalizedRot = ((rotationDegrees % 360) + 360) % 360
        val cacheKey = "${pageIndex}_${safeWidth}_${normalizedRot}_${invertColors}"

        pageCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val rendered = try {
            renderInternal(pageIndex, safeWidth, normalizedRot, invertColors)
        } catch (_: OutOfMemoryError) {
            // Free everything we can and retry once at a reduced resolution instead of crashing.
            pageCache.evictAll()
            thumbCache.evictAll()
            renderInternal(pageIndex, (safeWidth * 0.6f).toInt().coerceAtLeast(120), normalizedRot, invertColors)
        }
        pageCache.put(cacheKey, rendered)
        return rendered
    }


    /**
     * High-zoom tile renderer using MuPDF's AndroidDrawDevice(bitmap, patchX, patchY).
     *
     * Only the requested sub-rectangle is rasterised, at the density implied by
     * [zoomedFullPageWidthPx]. If the tile would exceed [MAX_TILE_SIDE] the density is reduced
     * (instead of cropping the tile, which previously produced a wrongly-framed bitmap), and the
     * returned [PdfRegionTile] reports exactly which normalized rectangle it covers.
     */
    override fun renderRegion(
        pageIndex: Int,
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float,
        zoomedFullPageWidthPx: Int,
        invertColors: Boolean
    ): PdfRegionTile = synchronized(lock) {
        check(!closed) { "Document is closed" }
        val page = document.loadPage(pageIndex)
        try {
            val bounds = page.bounds
            val pageW = max(1f, bounds.x1 - bounds.x0)
            val pageH = max(1f, bounds.y1 - bounds.y0)

            val l = leftNorm.coerceIn(0f, 0.995f)
            val t = topNorm.coerceIn(0f, 0.995f)
            val r = rightNorm.coerceIn(l + 0.005f, 1f)
            val b = bottomNorm.coerceIn(t + 0.005f, 1f)

            var scale = zoomedFullPageWidthPx.coerceIn(480, 9000).toFloat() / pageW
            val wantW = (r - l) * pageW * scale
            val wantH = (b - t) * pageH * scale
            val shrink = min(1f, min(MAX_TILE_SIDE / wantW, MAX_TILE_SIDE / wantH))
            scale *= shrink

            val fullW = pageW * scale
            val fullH = pageH * scale
            val patchX = floor(l * fullW).toInt()
            val patchY = floor(t * fullH).toInt()
            val endX = ceil(r * fullW).toInt()
            val endY = ceil(b * fullH).toInt()
            val tileW = (endX - patchX).coerceAtLeast(1)
            val tileH = (endY - patchY).coerceAtLeast(1)

            val tileBmp = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
            tileBmp.eraseColor(android.graphics.Color.WHITE)

            // Map page space -> device space, honouring non-zero page origins (cropped media boxes).
            val ctm = Matrix(scale, scale)
            ctm.e = -bounds.x0 * scale
            ctm.f = -bounds.y0 * scale

            val dev = AndroidDrawDevice(tileBmp, patchX, patchY)
            try {
                page.run(dev, ctm, null)
                dev.close()
            } finally {
                dev.destroy()
            }

            val finalBmp = if (invertColors) applyNightInversion(tileBmp) else tileBmp
            PdfRegionTile(
                bitmap = finalBmp,
                leftNorm = (patchX / fullW).coerceIn(0f, 1f),
                topNorm = (patchY / fullH).coerceIn(0f, 1f),
                rightNorm = ((patchX + tileW) / fullW).coerceIn(0f, 1f),
                bottomNorm = ((patchY + tileH) / fullH).coerceIn(0f, 1f)
            )
        } finally {
            page.destroy()
        }
    }

    override fun getPageLinks(pageIndex: Int): List<PdfLink> = synchronized(lock) {
        if (closed) return emptyList()
        val page = document.loadPage(pageIndex)
        try {
            val bounds = page.bounds
            val pageW = max(1f, bounds.x1 - bounds.x0)
            val pageH = max(1f, bounds.y1 - bounds.y0)
            val links = page.links ?: return emptyList()
            links.mapNotNull { link ->
                val b = link.bounds ?: return@mapNotNull null
                val uri: String? = link.uri
                val external = !uri.isNullOrBlank() && isExternalUri(uri)
                val target: Int? = if (external) {
                    null
                } else {
                    runCatching { document.pageNumberFromLocation(document.resolveLink(link)) }
                        .getOrNull()
                        ?.takeIf { it >= 0 }
                }
                if (!external && target == null) return@mapNotNull null
                PdfLink(
                    box = PdfGeometryHelper.normalizeBox(
                        minX = b.x0, minY = b.y0, maxX = b.x1, maxY = b.y1,
                        pageLeft = bounds.x0, pageTop = bounds.y0,
                        pageWidth = pageW, pageHeight = pageH
                    ),
                    targetPage = target,
                    uri = if (external) uri else null
                )
            }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            page.destroy()
        }
    }

    private fun isExternalUri(uri: String): Boolean {
        val lower = uri.trim().lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("mailto:") || lower.startsWith("tel:")
    }

    override fun renderThumbnail(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val safeWidth = targetWidthPx.coerceIn(96, 360)
        val cacheKey = "thumb_${pageIndex}_${safeWidth}"
        thumbCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        val rendered = renderInternal(pageIndex, safeWidth, 0, false)
        thumbCache.put(cacheKey, rendered)
        return rendered
    }

    private fun renderInternal(
        pageIndex: Int,
        targetWidthPx: Int,
        normalizedRot: Int,
        invertColors: Boolean
    ): Bitmap = synchronized(lock) {
        check(!closed) { "Document is closed" }
        val page = document.loadPage(pageIndex)
        try {
            val bounds = page.bounds
            val pageWidthPt = max(1f, bounds.x1 - bounds.x0)
            val pageHeightPt = max(1f, bounds.y1 - bounds.y0)
            val isQuarterTurn = normalizedRot == 90 || normalizedRot == 270
            val effectiveWidthPt = if (isQuarterTurn) pageHeightPt else pageWidthPt
            val scale = targetWidthPx.toFloat() / effectiveWidthPt

            val ctm = Matrix(scale, scale)
            if (normalizedRot != 0) {
                ctm.rotate(normalizedRot.toFloat())
            }

            val baseBitmap = AndroidDrawDevice.drawPage(page, ctm)
            if (invertColors) applyNightInversion(baseBitmap) else baseBitmap
        } finally {
            page.destroy()
        }
    }

    override fun searchPage(pageIndex: Int, query: String): PdfSearchHit? = synchronized(lock) {
        if (closed || query.isBlank()) return null
        val page = document.loadPage(pageIndex)
        try {
            val bounds = page.bounds
            val pageW = max(1f, bounds.x1 - bounds.x0)
            val pageH = max(1f, bounds.y1 - bounds.y0)

            // Supports both MuPDF Quad[] and Quad[][] return shapes safely across versions
            val rawResult: Any? = page.search(query)
            val quads = collectQuads(rawResult)
            if (quads.isEmpty()) return null

            val boxes = quads.map { q ->
                val minX = minOf(q.ul_x, q.ur_x, q.ll_x, q.lr_x)
                val maxX = maxOf(q.ul_x, q.ur_x, q.ll_x, q.lr_x)
                val minY = minOf(q.ul_y, q.ur_y, q.ll_y, q.lr_y)
                val maxY = maxOf(q.ul_y, q.ur_y, q.ll_y, q.lr_y)
                PdfGeometryHelper.normalizeBox(
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    pageLeft = bounds.x0,
                    pageTop = bounds.y0,
                    pageWidth = pageW,
                    pageHeight = pageH
                )
            }
            PdfSearchHit(pageIndex = pageIndex, boxes = boxes)
        } finally {
            page.destroy()
        }
    }

    private fun collectQuads(raw: Any?): List<Quad> {
        val list = mutableListOf<Quad>()
        when (raw) {
            is Quad -> list.add(raw)
            is Array<*> -> raw.forEach { item -> list.addAll(collectQuads(item)) }
        }
        return list
    }

    override fun extractPageText(pageIndex: Int): String = synchronized(lock) {
        if (closed) return ""
        val page = document.loadPage(pageIndex)
        try {
            val stext = page.toStructuredText()
            try {
                val sb = StringBuilder()
                val blocks = stext.blocks ?: emptyArray()
                for (block in blocks) {
                    val lines = block?.lines ?: continue
                    for (line in lines) {
                        val chars = line?.chars ?: continue
                        for (ch in chars) {
                            if (ch != null && ch.c > 0) {
                                sb.append(Character.toChars(ch.c))
                            }
                        }
                        sb.appendLine()
                    }
                    sb.appendLine()
                }
                sb.toString().trim()
            } finally {
                stext.destroy()
            }
        } catch (_: Throwable) {
            ""
        } finally {
            page.destroy()
        }
    }

    private fun applyNightInversion(source: Bitmap): Bitmap {
        val inverted = Bitmap.createBitmap(
            source.width,
            source.height,
            source.config ?: Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(inverted)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        -0.92f, 0f, 0f, 0f, 238f,
                        0f, -0.92f, 0f, 0f, 238f,
                        0f, 0f, -0.92f, 0f, 238f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        source.recycle()
        return inverted
    }

    override fun close() {
        synchronized(lock) {
            if (!closed) {
                closed = true
                pageCache.evictAll()
                thumbCache.evictAll()
                document.destroy()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfEngineModule {
    @Binds
    @Singleton
    abstract fun bindPdfEngine(impl: MuPdfEngine): PdfEngine
}
