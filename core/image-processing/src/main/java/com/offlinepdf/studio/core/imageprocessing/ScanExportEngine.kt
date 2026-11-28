package com.offlinepdf.studio.core.imageprocessing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
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
            val wrote = context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
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
            val outStream = context.contentResolver.openOutputStream(destinationUri, "wt")
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
        return try {
            val stream = context.contentResolver.openOutputStream(destinationUri, "wt")
                ?: return ScanExportResult.Failure(null, "Unable to open output stream for PDF.")
            var failure: ScanExportResult.Failure? = null
            stream.use { raw ->
                val writer = JpegPdfWriter(raw.buffered(64 * 1024), options.title)
                for ((index, item) in pages.withIndex()) {
                    val pageNum = index + 1
                    onProgress(pageNum, pages.size)
                    val bmp = processor.decodeDownsampledBitmap(item.enhancedImagePath, maxSide = 2600)
                    if (bmp == null) {
                        failure = ScanExportResult.Failure(
                            pageNum,
                            "Export aborted: Page #$pageNum is missing or unreadable. All originals preserved."
                        )
                        break
                    }
                    try {
                        val baos = ByteArrayOutputStream(512 * 1024)
                        bmp.compress(Bitmap.CompressFormat.JPEG, options.jpegQuality.coerceIn(40, 100), baos)
                        val (pageW, pageH) = ExportLayoutMath.resolvePageDimensionsPt(
                            options.pageSize, options.orientation, bmp.width, bmp.height
                        )
                        val margin = options.margin.points.toFloat()
                        val availW = (pageW - margin * 2f).coerceAtLeast(10f)
                        val availH = (pageH - margin * 2f).coerceAtLeast(10f)
                        val scale = min(availW / bmp.width, availH / bmp.height)
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        writer.addPage(
                            jpeg = baos.toByteArray(), imgW = bmp.width, imgH = bmp.height,
                            pageW = pageW.toFloat(), pageH = pageH.toFloat(),
                            x = margin + (availW - drawW) / 2f,
                            y = margin + (availH - drawH) / 2f,
                            w = drawW, h = drawH
                        )
                    } finally {
                        bmp.recycle()
                    }
                }
                if (failure == null) writer.finish()
            }
            failure?.also { deletePartial(destinationUri) }
                ?: ScanExportResult.Success(pages.size, isZipArchive = false)
        } catch (t: Throwable) {
            deletePartial(destinationUri)
            ScanExportResult.Failure(null, t.localizedMessage ?: "PDF export failed. Originals preserved.")
        }
    }

    /** Never leave a zero-byte / truncated file behind after a failed export. */
    private fun deletePartial(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }
}
