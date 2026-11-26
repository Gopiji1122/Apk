package com.offlinepdf.studio.core.imageprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.offlinepdf.studio.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

object ExportLayoutMath {
    fun resolvePageDimensionsPt(
        pageSize: ExportPageSize,
        orientation: ExportOrientation,
        imageWidthPx: Int,
        imageHeightPx: Int
    ): Pair<Int, Int> {
        if (pageSize == ExportPageSize.ORIGINAL) {
            val scale = 72f / 150f
            val w = (imageWidthPx * scale).toInt().coerceIn(200, 1440)
            val h = (imageHeightPx * scale).toInt().coerceIn(200, 2000)
            return w to h
        }
        val baseW = pageSize.widthPt
        val baseH = pageSize.heightPt
        val wantLandscape = when (orientation) {
            ExportOrientation.PORTRAIT -> false
            ExportOrientation.LANDSCAPE -> true
            ExportOrientation.AUTO -> imageWidthPx > imageHeightPx
        }
        return if (wantLandscape) baseH to baseW else baseW to baseH
    }
}

@Singleton
class ScanExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: OpenCvDocumentProcessor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Streams scanned pages incrementally from disk into a multi-page PDF (or single JPEG/PNG)
     * written directly to the user-selected SAF [destinationUri]. Never keeps all full-res pages in RAM.
     */
    suspend fun exportPages(
        pages: List<ScannedPageItem>,
        options: ExportOptions,
        destinationUri: Uri,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(ioDispatcher) {
        if (pages.isEmpty()) return@withContext false

        when (options.format) {
            ExportFormat.PDF -> exportAsPdf(pages, options, destinationUri, onProgress)
            ExportFormat.JPEG -> exportSingleImage(pages.first(), destinationUri, Bitmap.CompressFormat.JPEG, options.jpegQuality)
            ExportFormat.PNG -> exportSingleImage(pages.first(), destinationUri, Bitmap.CompressFormat.PNG, 100)
        }
    }

    private fun exportSingleImage(
        page: ScannedPageItem,
        destinationUri: Uri,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Boolean {
        val bmp = processor.decodeDownsampledBitmap(page.enhancedImagePath, maxSide = 2400) ?: return false
        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                bmp.compress(format, quality.coerceIn(40, 100), out)
            } ?: false
        } finally {
            bmp.recycle()
        }
    }

    private fun exportAsPdf(
        pages: List<ScannedPageItem>,
        options: ExportOptions,
        destinationUri: Uri,
        onProgress: (Int, Int) -> Unit
    ): Boolean {
        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        return try {
            pages.forEachIndexed { index, item ->
                onProgress(index + 1, pages.size)
                val rawBmp = processor.decodeDownsampledBitmap(item.enhancedImagePath, maxSide = 2000)
                    ?: return@forEachIndexed

                // Apply requested JPEG quality compression before embedding so file size honors quality slider
                val compressedBmp = recompressIfNeeded(rawBmp, options.jpegQuality)
                if (compressedBmp !== rawBmp) rawBmp.recycle()

                try {
                    val (pageW, pageH) = ExportLayoutMath.resolvePageDimensionsPt(
                        pageSize = options.pageSize,
                        orientation = options.orientation,
                        imageWidthPx = compressedBmp.width,
                        imageHeightPx = compressedBmp.height
                    )
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas: Canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val margin = options.margin.points.toFloat()
                    val availW = (pageW - margin * 2f).coerceAtLeast(10f)
                    val availH = (pageH - margin * 2f).coerceAtLeast(10f)
                    val scale = min(availW / compressedBmp.width.toFloat(), availH / compressedBmp.height.toFloat())
                    val drawW = compressedBmp.width * scale
                    val drawH = compressedBmp.height * scale
                    val left = margin + (availW - drawW) / 2f
                    val top = margin + (availH - drawH) / 2f

                    canvas.drawBitmap(
                        compressedBmp,
                        null,
                        RectF(left, top, left + drawW, top + drawH),
                        paint
                    )
                    pdfDocument.finishPage(page)
                } finally {
                    compressedBmp.recycle()
                }
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                pdfDocument.writeTo(out)
                true
            } ?: false
        } catch (_: Throwable) {
            false
        } finally {
            pdfDocument.close()
        }
    }

    private fun recompressIfNeeded(source: Bitmap, quality: Int): Bitmap {
        if (quality >= 95) return source
        val baos = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(35, 95), baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: source
    }
}
