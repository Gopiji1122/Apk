package com.gopiji1122.aurapdfeditor.core.pdfengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PdfMetadata(
    val title: String,
    val author: String,
    val subject: String,
    val creator: String,
    val pageCount: Int
)

object PdfEngine {

    private fun copyUriToTemp(context: Context, uri: Uri, prefix: String = "temp"): File {
        val file = File.createTempFile(prefix, ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file
    }

    private fun writeFileToUri(context: Context, file: File, destUri: Uri) {
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        }
    }

    suspend fun createBlankPdf(context: Context, destUri: Uri, pageCount: Int = 1): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                PDDocument().use { doc ->
                    repeat(pageCount.coerceIn(1, 50)) {
                        doc.addPage(PDPage(PDRectangle.A4))
                    }
                    val out = File.createTempFile("blank", ".pdf", context.cacheDir)
                    doc.save(out)
                    writeFileToUri(context, out, destUri)
                    out.delete()
                }
                "Created blank PDF ($pageCount page${if (pageCount > 1) "s" else ""})"
            }
        }

    suspend fun mergePdfs(context: Context, uris: List<Uri>, destUri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(uris.size >= 2) { "Select at least 2 PDF files to merge." }
                val tempFiles = uris.mapIndexed { idx, u -> copyUriToTemp(context, u, "merge_$idx") }
                val outFile = File.createTempFile("merged", ".pdf", context.cacheDir)
                try {
                    val merger = PDFMergerUtility()
                    merger.destinationFileName = outFile.absolutePath
                    tempFiles.forEach { merger.addSource(it) }
                    merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
                    writeFileToUri(context, outFile, destUri)
                    "Merged ${uris.size} PDFs successfully"
                } finally {
                    tempFiles.forEach { it.delete() }
                    outFile.delete()
                }
            }
        }

    suspend fun splitExtractPages(
        context: Context,
        sourceUri: Uri,
        pageRanges: String,
        destUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val src = copyUriToTemp(context, sourceUri, "split_src")
            val out = File.createTempFile("split_out", ".pdf", context.cacheDir)
            try {
                PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val total = doc.numberOfPages
                    val pagesToKeep = parsePageRanges(pageRanges, total)
                    require(pagesToKeep.isNotEmpty()) { "No valid pages in range (1..$total)." }
                    PDDocument().use { newDoc ->
                        pagesToKeep.forEach { pageNum ->
                            newDoc.importPage(doc.getPage(pageNum - 1))
                        }
                        newDoc.save(out)
                    }
                    writeFileToUri(context, out, destUri)
                    "Extracted ${pagesToKeep.size} page(s) successfully"
                }
            } finally {
                src.delete()
                out.delete()
            }
        }
    }

    suspend fun rotateAllPages(
        context: Context,
        sourceUri: Uri,
        degrees: Int,
        destUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val src = copyUriToTemp(context, sourceUri, "rot_src")
            val out = File.createTempFile("rot_out", ".pdf", context.cacheDir)
            try {
                PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    for (i in 0 until doc.numberOfPages) {
                        val page = doc.getPage(i)
                        page.rotation = (page.rotation + degrees) % 360
                    }
                    doc.save(out)
                }
                writeFileToUri(context, out, destUri)
                "Rotated all pages by ${degrees}°"
            } finally {
                src.delete()
                out.delete()
            }
        }
    }

    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        destUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(imageUris.isNotEmpty()) { "Select at least one image." }
            val out = File.createTempFile("img2pdf", ".pdf", context.cacheDir)
            try {
                PDDocument().use { doc ->
                    imageUris.forEach { uri ->
                        val bitmap = context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        } ?: return@forEach
                        val page = PDPage(PDRectangle.A4)
                        doc.addPage(page)
                        val pdImage = JPEGFactory.createFromImage(doc, bitmap, 0.85f)
                        val pageW = page.mediaBox.width
                        val pageH = page.mediaBox.height
                        val margin = 24f
                        val maxW = pageW - margin * 2
                        val maxH = pageH - margin * 2
                        val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
                        val drawW = bitmap.width * scale
                        val drawH = bitmap.height * scale
                        val x = (pageW - drawW) / 2f
                        val y = (pageH - drawH) / 2f
                        PDPageContentStream(doc, page).use { cs ->
                            cs.drawImage(pdImage, x, y, drawW, drawH)
                        }
                        bitmap.recycle()
                    }
                    doc.save(out)
                }
                writeFileToUri(context, out, destUri)
                "Created PDF from ${imageUris.size} image(s)"
            } finally {
                out.delete()
            }
        }
    }

    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        jpegQuality: Float,
        destUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val src = copyUriToTemp(context, sourceUri, "comp_src")
            val out = File.createTempFile("comp_out", ".pdf", context.cacheDir)
            val originalKb = src.length() / 1024
            try {
                val pfd = ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                PDDocument().use { newDoc ->
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val scale = if (jpegQuality < 0.5f) 1.0f else 1.5f
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                        newDoc.addPage(pdPage)
                        val pdImage = JPEGFactory.createFromImage(newDoc, bmp, jpegQuality)
                        PDPageContentStream(newDoc, pdPage).use { cs ->
                            cs.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                        }
                        bmp.recycle()
                        page.close()
                    }
                    newDoc.save(out)
                }
                renderer.close()
                pfd.close()
                val newKb = out.length() / 1024
                writeFileToUri(context, out, destUri)
                "Compressed: ${originalKb} KB -> ${newKb} KB"
            } finally {
                src.delete()
                out.delete()
            }
        }
    }

    suspend fun addWatermarkAndPageNumbers(
        context: Context,
        sourceUri: Uri,
        watermarkText: String,
        addPageNumbers: Boolean,
        destUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val src = copyUriToTemp(context, sourceUri, "wm_src")
            val out = File.createTempFile("wm_out", ".pdf", context.cacheDir)
            try {
                PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val total = doc.numberOfPages
                    for (i in 0 until total) {
                        val page = doc.getPage(i)
                        val box = page.mediaBox
                        PDPageContentStream(
                            doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                        ).use { cs ->
                            if (watermarkText.isNotBlank()) {
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                                cs.setNonStrokingColor(180, 180, 180)
                                cs.setTextMatrix(
                                    Matrix.getRotateInstance(
                                        Math.toRadians(45.0),
                                        box.width / 4f,
                                        box.height / 3f
                                    )
                                )
                                cs.showText(watermarkText)
                                cs.endText()
                            }
                            if (addPageNumbers) {
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, 11f)
                                cs.setNonStrokingColor(80, 80, 80)
                                cs.newLineAtOffset(box.width / 2f - 20f, 18f)
                                cs.showText("${i + 1} / $total")
                                cs.endText()
                            }
                        }
                    }
                    doc.save(out)
                }
                writeFileToUri(context, out, destUri)
                "Applied watermark / page numbers"
            } finally {
                src.delete()
                out.delete()
            }
        }
    }

    suspend fun protectPdf(
        context: Context,
        sourceUri: Uri,
        password: String,
        destUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(password.isNotBlank()) { "Password cannot be empty." }
            val src = copyUriToTemp(context, sourceUri, "lock_src")
            val out = File.createTempFile("lock_out", ".pdf", context.cacheDir)
            try {
                PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val ap = AccessPermission()
                    val spp = StandardProtectionPolicy(password, password, ap)
                    spp.encryptionKeyLength = 256
                    doc.protect(spp)
                    doc.save(out)
                }
                writeFileToUri(context, out, destUri)
                "PDF encrypted with AES-256"
            } finally {
                src.delete()
                out.delete()
            }
        }
    }

    suspend fun unlockPdf(
        context: Context,
        sourceUri: Uri,
        password: String,
        destUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val src = copyUriToTemp(context, sourceUri, "unlock_src")
            val out = File.createTempFile("unlock_out", ".pdf", context.cacheDir)
            try {
                PDDocument.load(src, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    doc.save(out)
                }
                writeFileToUri(context, out, destUri)
                "PDF unlocked successfully"
            } finally {
                src.delete()
                out.delete()
            }
        }
    }

    suspend fun scrubMetadata(
        context: Context,
        sourceUri: Uri,
        destUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val src = copyUriToTemp(context, sourceUri, "meta_src")
            val out = File.createTempFile("meta_out", ".pdf", context.cacheDir)
            try {
                PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    doc.documentInformation = PDDocumentInformation()
                    doc.save(out)
                }
                writeFileToUri(context, out, destUri)
                "All hidden metadata scrubbed"
            } finally {
                src.delete()
                out.delete()
            }
        }
    }

    suspend fun extractPageText(context: Context, sourceUri: Uri, pageIndex: Int): String =
        withContext(Dispatchers.IO) {
            val src = copyUriToTemp(context, sourceUri, "txt_src")
            try {
                PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(doc).trim()
                }
            } catch (_: Exception) {
                ""
            } finally {
                src.delete()
            }
        }

    suspend fun searchInPdf(context: Context, sourceUri: Uri, query: String): List<Int> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val src = copyUriToTemp(context, sourceUri, "search_src")
            val matches = mutableListOf<Int>()
            try {
                PDDocument.load(src, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
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
            } catch (_: Exception) {
            } finally {
                src.delete()
            }
            matches
        }

    private fun parsePageRanges(input: String, maxPage: Int): List<Int> {
        val result = linkedSetOf<Int>()
        input.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            if ("-" in part) {
                val bounds = part.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (bounds.size == 2) {
                    val start = bounds[0].coerceIn(1, maxPage)
                    val end = bounds[1].coerceIn(1, maxPage)
                    if (start <= end) {
                        for (p in start..end) result.add(p)
                    }
                }
            } else {
                part.toIntOrNull()?.let { p ->
                    if (p in 1..maxPage) result.add(p)
                }
            }
        }
        return result.toList()
    }
}
