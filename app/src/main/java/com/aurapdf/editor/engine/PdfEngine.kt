package com.aurapdf.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.FileOutputStream

data class PdfLinkInfo(
    val index: Int,
    val pageIndex: Int,
    val url: String
)

data class CreatorElement(
    val id: Long = System.nanoTime(),
    val pageIndex: Int = 0,
    val isImage: Boolean = false,
    val text: String = "",
    val fontSize: Float = 14f,
    val isBold: Boolean = false,
    val colorHex: Int = 0x000000,
    val linkUrl: String = "",
    val imageUri: Uri? = null,
    val xPercent: Float = 0.08f,
    val yPercent: Float = 0.12f,
    val widthPercent: Float = 0.65f
)

object PdfEngine {

    private fun sanitize(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.code in 32..126) sb.append(ch) else sb.append(' ')
        }
        return sb.toString()
    }

    fun getPageCount(file: File): Int {
        PDDocument.load(file).use { return it.numberOfPages }
    }

    fun renderPageBitmap(file: File, pageIndex: Int, targetWidth: Int = 1080): Bitmap {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val safeIdx = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
        val page = renderer.openPage(safeIdx)
        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(
            (page.width * scale).toInt().coerceAtLeast(1),
            (page.height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()
        return bmp
    }

    // Full In-App PDF Creator (Blank PDF + Multiple Pages + Text + JPG/PNG Images + Clickable Links + Page Numbers)
    fun createCustomPdf(
        context: Context,
        outputFile: File,
        pageCount: Int,
        pageSizeName: String,
        landscape: Boolean,
        addPageNumbers: Boolean,
        titleMeta: String,
        authorMeta: String,
        elements: List<CreatorElement>
    ): Int {
        val baseRect = when (pageSizeName) {
            "Letter" -> PDRectangle.LETTER
            "Legal" -> PDRectangle.LEGAL
            else -> PDRectangle.A4
        }
        val rect = if (landscape) PDRectangle(baseRect.height, baseRect.width) else baseRect

        val doc = PDDocument()
        if (titleMeta.isNotBlank()) doc.documentInformation.title = titleMeta
        if (authorMeta.isNotBlank()) doc.documentInformation.author = authorMeta

        for (p in 0 until pageCount.coerceAtLeast(1)) {
            val page = PDPage(rect)
            doc.addPage(page)
            val pw = rect.width
            val ph = rect.height
            val cs = PDPageContentStream(doc, page)

            val pageItems = elements.filter { it.pageIndex == p }
            for (el in pageItems) {
                val x = (el.xPercent.coerceIn(0.02f, 0.90f)) * pw
                val topY = ph - (el.yPercent.coerceIn(0.04f, 0.94f)) * ph

                if (el.isImage && el.imageUri != null) {
                    context.contentResolver.openInputStream(el.imageUri)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        if (bmp != null) {
                            val pdImg = LosslessFactory.createFromImage(doc, bmp)
                            val drawW = (el.widthPercent.coerceIn(0.15f, 0.92f)) * pw
                            val aspect = bmp.height.toFloat() / bmp.width.coerceAtLeast(1)
                            val drawH = (drawW * aspect).coerceAtMost(ph * 0.78f)
                            val drawY = (topY - drawH).coerceAtLeast(24f)
                            cs.drawImage(pdImg, x, drawY, drawW, drawH)
                            bmp.recycle()
                        }
                    }
                } else if (!el.isImage && el.text.isNotBlank()) {
                    val font = if (el.isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                    val r = (el.colorHex shr 16) and 0xFF
                    val g = (el.colorHex shr 8) and 0xFF
                    val b = el.colorHex and 0xFF
                    val hasLink = el.linkUrl.isNotBlank()

                    if (hasLink) {
                        cs.setNonStrokingColor(25, 118, 210)
                    } else {
                        cs.setNonStrokingColor(r, g, b)
                    }

                    val lines = el.text.split("\n")
                    var curY = topY
                    lines.forEach { rawLine ->
                        val clean = sanitize(rawLine)
                        cs.beginText()
                        cs.setFont(font, el.fontSize)
                        cs.newLineAtOffset(x, curY)
                        cs.showText(clean)
                        cs.endText()

                        if (hasLink && clean.isNotEmpty()) {
                            val textW = (font.getStringWidth(clean) / 1000f) * el.fontSize
                            // Draw underline
                            cs.setStrokingColor(25, 118, 210)
                            cs.setLineWidth(0.8f)
                            cs.moveTo(x, curY - 2f)
                            cs.lineTo(x + textW, curY - 2f)
                            cs.stroke()

                            // Attach real PDF clickable URI annotation
                            val link = PDAnnotationLink()
                            val border = PDBorderStyleDictionary().apply { width = 0f }
                            link.borderStyle = border
                            link.rectangle = PDRectangle(x, curY - 3f, textW.coerceAtLeast(24f), el.fontSize + 4f)
                            val action = PDActionURI().apply {
                                uri = if (el.linkUrl.startsWith("http://") || el.linkUrl.startsWith("https://")) {
                                    el.linkUrl
                                } else {
                                    "https://${el.linkUrl}"
                                }
                            }
                            link.action = action
                            page.annotations.add(link)
                        }
                        curY -= (el.fontSize * 1.35f)
                    }
                    cs.setNonStrokingColor(0, 0, 0)
                }
            }

            if (addPageNumbers) {
                cs.setNonStrokingColor(120, 120, 120)
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 10f)
                cs.newLineAtOffset(pw / 2f - 18f, 22f)
                cs.showText("Page ${p + 1} / $pageCount")
                cs.endText()
            }

            cs.close()
        }

        doc.save(outputFile)
        val total = doc.numberOfPages
        doc.close()
        return total
    }

    // List all hyperlinks inside a PDF
    fun listLinks(file: File): List<PdfLinkInfo> {
        val result = mutableListOf<PdfLinkInfo>()
        PDDocument.load(file).use { doc ->
            for (p in 0 until doc.numberOfPages) {
                val annots = doc.getPage(p).annotations
                annots.forEachIndexed { idx, ann ->
                    if (ann is PDAnnotationLink) {
                        val uri = (ann.action as? PDActionURI)?.uri ?: "(Internal Link)"
                        result.add(PdfLinkInfo(idx, p, uri))
                    }
                }
            }
        }
        return result
    }

    // Add a clickable link (with optional text or invisible overlay) to an existing PDF page
    fun addLinkToPdf(
        inputFile: File,
        outputFile: File,
        pageIndex: Int,
        labelText: String,
        url: String,
        xPercent: Float,
        yPercent: Float,
        fontSize: Float = 13f
    ) {
        PDDocument.load(inputFile).use { doc ->
            val safeIdx = pageIndex.coerceIn(0, doc.numberOfPages - 1)
            val page = doc.getPage(safeIdx)
            val pw = page.mediaBox.width
            val ph = page.mediaBox.height
            val x = xPercent.coerceIn(0.03f, 0.88f) * pw
            val y = ph - (yPercent.coerceIn(0.05f, 0.94f) * ph)
            val finalUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"

            var boxW = 140f
            if (labelText.isNotBlank()) {
                val clean = sanitize(labelText)
                val font = PDType1Font.HELVETICA_BOLD
                boxW = ((font.getStringWidth(clean) / 1000f) * fontSize).coerceAtLeast(36f)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setNonStrokingColor(25, 118, 210)
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.newLineAtOffset(x, y)
                    cs.showText(clean)
                    cs.endText()
                    cs.setStrokingColor(25, 118, 210)
                    cs.setLineWidth(0.9f)
                    cs.moveTo(x, y - 2f)
                    cs.lineTo(x + boxW, y - 2f)
                    cs.stroke()
                }
            }

            val link = PDAnnotationLink().apply {
                borderStyle = PDBorderStyleDictionary().apply { width = 0f }
                rectangle = PDRectangle(x, y - 4f, boxW, fontSize + 8f)
                action = PDActionURI().apply { uri = finalUrl }
            }
            page.annotations.add(link)
            doc.save(outputFile)
        }
    }

    // Remove a single link or all links from a PDF
    fun removeLinks(inputFile: File, outputFile: File, targetPage: Int? = null, targetUrl: String? = null): Int {
        var removed = 0
        PDDocument.load(inputFile).use { doc ->
            for (p in 0 until doc.numberOfPages) {
                if (targetPage != null && p != targetPage) continue
                val page = doc.getPage(p)
                val annots = page.annotations
                val toRemove = annots.filter { ann ->
                    if (ann !is PDAnnotationLink) return@filter false
                    if (targetUrl == null) true
                    else ((ann.action as? PDActionURI)?.uri ?: "(Internal Link)") == targetUrl
                }
                if (toRemove.isNotEmpty()) {
                    annots.removeAll(toRemove.toSet())
                    page.annotations = annots
                    removed += toRemove.size
                }
            }
            doc.save(outputFile)
        }
        return removed
    }

    // Images (JPG/PNG) to PDF
    fun imagesToPdf(context: Context, uris: List<Uri>, outputFile: File): Int {
        val doc = PDDocument()
        for (uri in uris) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return@use
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val pw = page.mediaBox.width
                val ph = page.mediaBox.height
                val margin = 24f
                val maxW = pw - margin * 2
                val maxH = ph - margin * 2
                val scale = minOf(maxW / bmp.width, maxH / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val dx = (pw - dw) / 2f
                val dy = (ph - dh) / 2f
                val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.88f)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImg, dx, dy, dw, dh)
                }
                bmp.recycle()
            }
        }
        doc.save(outputFile)
        val count = doc.numberOfPages
        doc.close()
        return count
    }

    // Merge multiple PDFs
    fun mergePdfs(files: List<File>, outputFile: File) {
        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath
        files.forEach { merger.addSource(it) }
        merger.mergeDocuments(null)
    }

    // Split PDF by 1-based inclusive range
    fun splitPdf(inputFile: File, outputFile: File, fromPage1: Int, toPage1: Int): Int {
        PDDocument.load(inputFile).use { src ->
            val start = (fromPage1 - 1).coerceIn(0, src.numberOfPages - 1)
            val end = (toPage1 - 1).coerceIn(start, src.numberOfPages - 1)
            val dst = PDDocument()
            for (i in start..end) {
                dst.importPage(src.getPage(i))
            }
            dst.save(outputFile)
            val count = dst.numberOfPages
            dst.close()
            return count
        }
    }

    // Rotate page or all pages
    fun rotatePdf(inputFile: File, outputFile: File, degrees: Int, pageIndex: Int? = null) {
        PDDocument.load(inputFile).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                if (pageIndex == null || i == pageIndex) {
                    val p = doc.getPage(i)
                    p.rotation = (p.rotation + degrees) % 360
                }
            }
            doc.save(outputFile)
        }
    }

    // Delete specific 1-based page
    fun deletePage(inputFile: File, outputFile: File, pageNumber1: Int) {
        PDDocument.load(inputFile).use { doc ->
            if (doc.numberOfPages <= 1) throw IllegalStateException("Cannot delete the only page in a PDF.")
            val idx = (pageNumber1 - 1).coerceIn(0, doc.numberOfPages - 1)
            doc.removePage(idx)
            doc.save(outputFile)
        }
    }

    // Add diagonal watermark to all pages
    fun addWatermark(inputFile: File, outputFile: File, text: String) {
        val clean = sanitize(text.ifBlank { "AURA PDF" })
        PDDocument.load(inputFile).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val pw = page.mediaBox.width
                val ph = page.mediaBox.height
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setNonStrokingColor(180, 180, 180)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 42f)
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45.0), pw * 0.22f, ph * 0.35f))
                    cs.showText(clean)
                    cs.endText()
                }
            }
            doc.save(outputFile)
        }
    }

    // Stamp signature bitmap onto PDF page
    fun stampSignature(inputFile: File, outputFile: File, signatureBmp: Bitmap, pageIndex: Int, xPercent: Float, yPercent: Float) {
        PDDocument.load(inputFile).use { doc ->
            val safeIdx = pageIndex.coerceIn(0, doc.numberOfPages - 1)
            val page = doc.getPage(safeIdx)
            val pw = page.mediaBox.width
            val ph = page.mediaBox.height
            val pdImg = LosslessFactory.createFromImage(doc, signatureBmp)
            val w = pw * 0.32f
            val h = w * (signatureBmp.height.toFloat() / signatureBmp.width.coerceAtLeast(1))
            val x = xPercent.coerceIn(0.05f, 0.65f) * pw
            val y = ph - (yPercent.coerceIn(0.10f, 0.90f) * ph) - h
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.drawImage(pdImg, x, y.coerceAtLeast(16f), w, h)
            }
            doc.save(outputFile)
        }
    }

    // Password protect (encrypt) PDF
    fun protectPdf(inputFile: File, outputFile: File, password: String) {
        PDDocument.load(inputFile).use { doc ->
            val ap = AccessPermission()
            val spp = StandardProtectionPolicy(password, password, ap).apply {
                encryptionKeyLength = 128
            }
            doc.protect(spp)
            doc.save(outputFile)
        }
    }

    // Unlock password-protected PDF
    fun unlockPdf(inputFile: File, outputFile: File, password: String) {
        PDDocument.load(inputFile, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(outputFile)
        }
    }

    // Save PDF page as PNG image
    fun exportPageAsPng(inputFile: File, pageIndex: Int, outPng: File) {
        val bmp = renderPageBitmap(inputFile, pageIndex, 1440)
        FileOutputStream(outPng).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bmp.recycle()
    }
}
