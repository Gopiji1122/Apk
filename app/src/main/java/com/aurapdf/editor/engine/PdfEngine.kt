package com.aurapdf.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfEngine {

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.pageCount
            }
        }
    }

    /** Merge multiple PDFs into one output file */
    suspend fun mergePdfs(context: Context, uris: List<Uri>, outputFile: File): File =
        withContext(Dispatchers.IO) {
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath
            val streams = uris.mapNotNull { context.contentResolver.openInputStream(it) }
            try {
                streams.forEach { merger.addSource(it) }
                merger.mergeDocuments(null)
            } finally {
                streams.forEach { runCatching { it.close() } }
            }
            outputFile
        }

    /** Split / extract a 1-based inclusive page range */
    suspend fun splitPdf(inputFile: File, startPage: Int, endPage: Int, outputFile: File): File =
        withContext(Dispatchers.IO) {
            PDDocument.load(inputFile).use { source ->
                PDDocument().use { target ->
                    val safeStart = (startPage - 1).coerceIn(0, source.numberOfPages - 1)
                    val safeEnd = (endPage - 1).coerceIn(safeStart, source.numberOfPages - 1)
                    for (i in safeStart..safeEnd) {
                        target.importPage(source.getPage(i))
                    }
                    target.save(outputFile)
                }
            }
            outputFile
        }

    /** Reorder, rotate, and keep only selected pages in one fast pass */
    suspend fun applyPageEdits(
        inputFile: File,
        pageSpecs: List<Pair<Int, Int>>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        PDDocument.load(inputFile).use { source ->
            PDDocument().use { target ->
                for ((origIndex, extraRotation) in pageSpecs) {
                    if (origIndex in 0 until source.numberOfPages) {
                        val imported = target.importPage(source.getPage(origIndex))
                        imported.rotation = (imported.rotation + extraRotation) % 360
                    }
                }
                target.save(outputFile)
            }
        }
        outputFile
    }

    /** Lock PDF with AES-128 password protection */
    suspend fun lockPdf(inputFile: File, password: String, outputFile: File): File =
        withContext(Dispatchers.IO) {
            PDDocument.load(inputFile).use { doc ->
                val permissions = AccessPermission()
                val policy = StandardProtectionPolicy(password, password, permissions).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(policy)
                doc.save(outputFile)
            }
            outputFile
        }

    /** Unlock password-protected PDF */
    suspend fun unlockPdf(inputFile: File, password: String, outputFile: File): File =
        withContext(Dispatchers.IO) {
            PDDocument.load(inputFile, password).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(outputFile)
            }
            outputFile
        }

    /** Add diagonal translucent watermark across all pages */
    suspend fun addWatermark(
        inputFile: File,
        watermarkText: String,
        opacity: Float = 0.22f,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        PDDocument.load(inputFile).use { doc ->
            for (page in doc.pages) {
                val box = page.cropBox ?: page.mediaBox
                val gs = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 0.8f)
                }
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.setGraphicsStateParameters(gs)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 44f)
                    val centerX = box.width * 0.22f
                    val centerY = box.height * 0.38f
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35.0), centerX, centerY))
                    cs.showText(watermarkText)
                    cs.endText()
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    /** Stamp a transparent bitmap (signature or flattened markup overlay) onto a page */
    suspend fun stampBitmapOnPage(
        inputFile: File,
        pageIndex: Int,
        bitmap: Bitmap,
        normX: Float = 0f,
        normY: Float = 0f,
        normW: Float = 1f,
        normH: Float = 1f,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        PDDocument.load(inputFile).use { doc ->
            val safeIndex = pageIndex.coerceIn(0, doc.numberOfPages - 1)
            val page = doc.getPage(safeIndex)
            val box = page.cropBox ?: page.mediaBox
            val x = box.width * normX
            val width = box.width * normW
            val height = box.height * normH
            val y = box.height * (1f - normY - normH)

            val pdImage = LosslessFactory.createFromImage(doc, bitmap)
            PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { cs ->
                cs.drawImage(pdImage, x, y, width, height)
            }
            doc.save(outputFile)
        }
        outputFile
    }

    /** Convert multiple images into a single PDF */
    suspend fun imagesToPdf(
        bitmaps: List<Bitmap>,
        outputFile: File,
        quality: Float = 0.82f
    ): File = withContext(Dispatchers.IO) {
        PDDocument().use { doc ->
            bitmaps.forEach { bmp ->
                val page = PDPage(PDRectangle(bmp.width.toFloat(), bmp.height.toFloat()))
                doc.addPage(page)
                val pdImage = JPEGFactory.createFromImage(doc, bmp, quality.coerceIn(0.2f, 1.0f))
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, 0f, 0f)
                }
            }
            FileOutputStream(outputFile).use { fos ->
                doc.save(fos)
            }
        }
        outputFile
    }

    /** Compress PDF offline by re-encoding pages at reduced resolution & JPEG quality */
    suspend fun compressPdf(
        inputFile: File,
        quality: Float,
        scaleFactor: Float,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                PDDocument().use { outDoc ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val w = (page.width * 1.5f * scaleFactor).toInt().coerceAtLeast(400)
                            val h = (page.height * 1.5f * scaleFactor).toInt().coerceAtLeast(400)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            outDoc.addPage(pdPage)
                            val pdImage = JPEGFactory.createFromImage(outDoc, bmp, quality)
                            PDPageContentStream(outDoc, pdPage).use { cs ->
                                cs.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                            }
                            bmp.recycle()
                        }
                    }
                    outDoc.save(outputFile)
                }
            }
        }
        outputFile
    }

    /** Export every page of a PDF to PNG files */
    suspend fun exportPagesToImages(inputFile: File, outputDir: File): List<File> =
        withContext(Dispatchers.IO) {
            if (!outputDir.exists()) outputDir.mkdirs()
            val exported = mutableListOf<File>()
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val targetW = 1440
                            val targetH = (targetW.toFloat() / page.width * page.height).toInt()
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val imgFile = File(outputDir, "${inputFile.nameWithoutExtension}_page_${i + 1}.png")
                            FileOutputStream(imgFile).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            }
                            bmp.recycle()
                            exported.add(imgFile)
                        }
                    }
                }
            }
            exported
        }

    /** Search embedded text inside PDF; returns 0-based matching page indices */
    suspend fun searchTextInPdf(inputFile: File, query: String): List<Int> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val matches = mutableListOf<Int>()
            PDDocument.load(inputFile).use { doc ->
                val stripper = PDFTextStripper()
                for (i in 1..doc.numberOfPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    val text = stripper.getText(doc)
                    if (text.contains(query, ignoreCase = true)) {
                        matches.add(i - 1)
                    }
                }
            }
            matches
        }
}
