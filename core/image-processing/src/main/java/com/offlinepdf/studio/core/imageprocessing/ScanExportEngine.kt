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
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

sealed interface ScanExportResult {
    data class Success(val exportedPages: Int, val isZipArchive: Boolean = false) : ScanExportResult
    data class Failure(val failedPageNumber: Int?, val reason: String) : ScanExportResult
}

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
     * Data-safe exporter:
     * - PDF: Aborts immediately if ANY page is missing or corrupt (never silently omits a page).
     * - JPEG/PNG with 1 page: Writes that image directly.
     * - JPEG/PNG with multiple pages: Writes ALL pages into a ZIP archive so zero pages are lost!
     */
    suspend fun exportPages(
        pages: List<ScannedPageItem>,
        options: ExportOptions,
        destinationUri: Uri,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): ScanExportResult = withContext(ioDispatcher) {
        if (pages.isEmpty()) {
            return@withContext ScanExportResult.Failure(null, "No pages to export.")
        }

        when (options.format) {
            ExportFormat.PDF -> exportAsPdf(pages, options, destinationUri, onProgress)
            ExportFormat.JPEG -> {
                if (pages.size == 1) {
                    exportSingleImage(pages.first(), destinationUri, Bitmap.CompressFormat.JPEG, options.jpegQuality)
                } else {
                    exportImagesAsZip(pages, destinationUri, Bitmap.CompressFormat.JPEG, "jpg", options.jpegQuality, onProgress)
                }
            }
            ExportFormat.PNG -> {
                if (pages.size == 1) {
                    exportSingleImage(pages.first(), destinationUri, Bitmap.CompressFormat.PNG, 100)
                } else {
                    exportImagesAsZip(pages, destinationUri, Bitmap.CompressFormat.PNG, "png", 100, onProgress)
                }
            }
        }
    }

    private fun exportSingleImage(
        page: ScannedPageItem,
        destinationUri: Uri,
        format: Bitmap.CompressFormat,
        quality: Int
    ): ScanExportResult {
        val bmp = processor.decodeDownsampledBitmap(page.enhancedImagePath, maxSide = 2600)
            ?: return ScanExportResult.Failure(1, "Page #1 could not be decoded. Originals preserved.")
        return try {
            val wrote = context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                bmp.compress(format, quality.coerceIn(40, 100), out)
            } ?: false
            if (wrote) ScanExportResult.Success(1, isZipArchive = false)
            else ScanExportResult.Failure(1, "Could not write output file.")
        } catch (t: Throwable) {
            ScanExportResult.Failure(1, t.localizedMessage ?: "Failed to write image.")
        } finally {
            bmp.recycle()
        }
    }

    private fun exportImagesAsZip(
        pages: List<ScannedPageItem>,
        destinationUri: Uri,
        format: Bitmap.CompressFormat,
        ext: String,
        quality: Int,
        onProgress: (Int, Int) -> Unit
    ): ScanExportResult {
        return try {
            val outStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return ScanExportResult.Failure(null, "Could not open destination file.")

            ZipOutputStream(outStream).use { zos ->
                pages.forEachIndexed { idx, item ->
                    val pageNum = idx + 1
                    onProgress(pageNum, pages.size)
                    val bmp = processor.decodeDownsampledBitmap(item.enhancedImagePath, maxSide = 2400)
                        ?: return ScanExportResult.Failure(
                            pageNum,
                            "Export aborted: Page #$pageNum could not be read. All originals preserved."
                        )
                    try {
                        val entryName = String.format(Locale.US, "Page_%03d.%s", pageNum, ext)
                        zos.putNextEntry(ZipEntry(entryName))
                        val ok = bmp.compress(format, quality.coerceIn(40, 100), zos)
                        zos.closeEntry()
                        if (!ok) {
                            return ScanExportResult.Failure(pageNum, "Failed to encode Page #$pageNum.")
                        }
                    } finally {
                        bmp.recycle()
                    }
                }
            }
            ScanExportResult.Success(pages.size, isZipArchive = true)
        } catch (t: Throwable) {
            ScanExportResult.Failure(null, t.localizedMessage ?: "ZIP export failed. Originals preserved.")
        }
    }

    private fun exportAsPdf(
        pages: List<ScannedPageItem>,
        options: ExportOptions,
        destinationUri: Uri,
        onProgress: (Int, Int) -> Unit
    ): ScanExportResult {
        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        return try {
            pages.forEachIndexed { index, item ->
                val pageNum = index + 1
                onProgress(pageNum, pages.size)
                val rawBmp = processor.decodeDownsampledBitmap(item.enhancedImagePath, maxSide = 2000)
                    ?: return ScanExportResult.Failure(
                        pageNum,
                        "Export aborted: Page #$pageNum is missing or unreadable. All originals preserved."
                    )

                val compressedBmp = recompressIfNeeded(rawBmp, options.jpegQuality)
                if (compressedBmp !== rawBmp) rawBmp.recycle()

                try {
                    val (pageW, pageH) = ExportLayoutMath.resolvePageDimensionsPt(
                        pageSize = options.pageSize,
                        orientation = options.orientation,
                        imageWidthPx = compressedBmp.width,
                        imageHeightPx = compressedBmp.height
                    )
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
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
                ScanExportResult.Success(pages.size, isZipArchive = false)
            } ?: ScanExportResult.Failure(null, "Unable to open output stream for PDF.")
        } catch (t: Throwable) {
            ScanExportResult.Failure(null, t.localizedMessage ?: "PDF export failed. Originals preserved.")
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
