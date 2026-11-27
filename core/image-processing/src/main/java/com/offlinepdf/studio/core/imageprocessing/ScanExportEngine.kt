package com.offlinepdf.studio.core.imageprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
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

/**
 * Positioned OCR word in normalized [0..1] page coordinates so Searchable PDF export
 * places an invisible selectable/searchable text layer directly over the scanned image.
 */
data class PositionedOcrWord(
    val text: String,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float
)

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
    suspend fun exportPages(
        pages: List<ScannedPageItem>,
        options: ExportOptions,
        destinationUri: Uri,
        ocrWordsByPageIndex: Map<Int, List<PositionedOcrWord>> = emptyMap(),
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): ScanExportResult = withContext(ioDispatcher) {
        if (pages.isEmpty()) {
            return@withContext ScanExportResult.Failure(null, "No pages to export.")
        }

        when (options.format) {
            ExportFormat.PDF, ExportFormat.SEARCHABLE_PDF ->
                exportAsPdf(pages, options, destinationUri, ocrWordsByPageIndex, onProgress)
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
                        if (!ok) return ScanExportResult.Failure(pageNum, "Failed to encode Page #$pageNum.")
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
        ocrWordsByPageIndex: Map<Int, List<PositionedOcrWord>>,
        onProgress: (Int, Int) -> Unit
    ): ScanExportResult {
        val pdfDocument = PdfDocument()
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val invisibleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(1, 0, 0, 0) // Visually transparent but searchable/selectable in PDF readers
        }

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

                    // Draw invisible positioned OCR text layer (if Searchable PDF)
                    val words = ocrWordsByPageIndex[index].orEmpty()
                    if (words.isNotEmpty()) {
                        for (w in words) {
                            val boxH = ((w.bottomNorm - w.topNorm) * drawH).coerceIn(6f, 48f)
                            invisibleTextPaint.textSize = boxH * 0.85f
                            val x = left + w.leftNorm * drawW
                            val y = top + w.bottomNorm * drawH
                            canvas.drawText(w.text, x, y, invisibleTextPaint)
                        }
                    }

                    // Draw scanned page visual image on top
                    canvas.drawBitmap(
                        compressedBmp,
                        null,
                        RectF(left, top, left + drawW, top + drawH),
                        imgPaint
                    )
                    pdfDocument.finishPage(page)
                } finally {
                    compressedBmp.recycle()
                }
            }

            val wrote = context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                pdfDocument.writeTo(out)
                out.flush()
                true
            } ?: false

            if (!wrote) {
                return ScanExportResult.Failure(null, "Unable to write PDF to destination.")
            }

            // Section 12 critical verification: verify written PDF is readable and pageCount matches!
            val verified = verifyExportedPdf(destinationUri, expectedPages = pages.size)
            if (!verified) {
                return ScanExportResult.Failure(
                    null,
                    "Export verification failed (page count mismatch or unreadable PDF). All originals preserved."
                )
            }

            ScanExportResult.Success(pages.size, isZipArchive = false)
        } catch (t: Throwable) {
            ScanExportResult.Failure(null, t.localizedMessage ?: "PDF export failed. Originals preserved.")
        } finally {
            pdfDocument.close()
        }
    }

    private fun verifyExportedPdf(uri: Uri, expectedPages: Int): Boolean {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount == expectedPages
                }
            } ?: false
        }.getOrDefault(false)
    }

    private fun recompressIfNeeded(source: Bitmap, quality: Int): Bitmap {
        if (quality >= 95) return source
        val baos = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(35, 95), baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: source
    }
}
