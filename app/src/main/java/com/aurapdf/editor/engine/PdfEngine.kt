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
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfLinkInfo(
    val index: Int,
    val pageIndex: Int,
    val url: String
)

enum class TextFontStyle(val label: String) {
    SANS("Sans"),
    SERIF("Serif"),
    MONO("Mono")
}

enum class OverlayKind {
    TEXT,
    IMAGE,
    HIGHLIGHT,
    WHITEOUT,
    REDACT,
    STICKY_NOTE,
    SHAPE_RECT,
    SHAPE_LINE
}

// Live Interactive Draft Overlay (touch-draggable, touch-resizable, re-editable until Save!)
data class DraftOverlay(
    val id: Long = System.nanoTime(),
    val kind: OverlayKind = OverlayKind.TEXT,
    val text: String = "",
    val fontSizeSp: Float = 16f,
    val fontStyle: TextFontStyle = TextFontStyle.SANS,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val hasBgHighlight: Boolean = false,
    val colorArgb: Long = 0xFF000000,
    val linkUrl: String = "",
    val imageBitmap: Bitmap? = null,
    val xNorm: Float = 0.12f,
    val yNorm: Float = 0.16f,
    val wNorm: Float = 0.42f,
    val hNorm: Float = 0.18f
)

// Freehand ink stroke in normalized 0..1 page coordinates
data class InkStroke(
    val points: List<Pair<Float, Float>>,
    val colorArgb: Long = 0xFFD32F2F,
    val strokeWidthPt: Float = 2.6f
)

data class PdfMetaInfo(
    val title: String,
    val author: String,
    val subject: String,
    val pageCount: Int
)

object PdfEngine {

    private fun sanitize(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.code in 32..126) sb.append(ch) else sb.append(' ')
        }
        return sb.toString()
    }

    private fun saveInPlace(doc: PDDocument, file: File) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        doc.save(tmp)
        doc.close()
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        file.setLastModified(System.currentTimeMillis())
    }

    private fun sha256Short(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }.take(16).uppercase()
    }

    private fun resolveFont(style: TextFontStyle, bold: Boolean, italic: Boolean): PDType1Font {
        return when (style) {
            TextFontStyle.SANS -> when {
                bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                bold -> PDType1Font.HELVETICA_BOLD
                italic -> PDType1Font.HELVETICA_OBLIQUE
                else -> PDType1Font.HELVETICA
            }
            TextFontStyle.SERIF -> when {
                bold && italic -> PDType1Font.TIMES_BOLD_ITALIC
                bold -> PDType1Font.TIMES_BOLD
                italic -> PDType1Font.TIMES_ITALIC
                else -> PDType1Font.TIMES_ROMAN
            }
            TextFontStyle.MONO -> when {
                bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE
                bold -> PDType1Font.COURIER_BOLD
                italic -> PDType1Font.COURIER_OBLIQUE
                else -> PDType1Font.COURIER
            }
        }
    }

    fun createBlankPdf(file: File, pageSize: String = "A4", landscape: Boolean = false) {
        val base = when (pageSize) {
            "Letter" -> PDRectangle.LETTER
            "Legal" -> PDRectangle.LEGAL
            else -> PDRectangle.A4
        }
        val rect = if (landscape) PDRectangle(base.height, base.width) else base
        val doc = PDDocument()
        doc.addPage(PDPage(rect))
        doc.save(file)
        doc.close()
    }

    fun bitmapsToPdf(bitmaps: List<Bitmap>, outputFile: File): Int {
        val doc = PDDocument()
        for (bmp in bitmaps) {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val pw = page.mediaBox.width
            val ph = page.mediaBox.height
            val margin = 12f
            val scale = minOf((pw - margin * 2) / bmp.width.coerceAtLeast(1), (ph - margin * 2) / bmp.height.coerceAtLeast(1))
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.90f)
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(pdImg, (pw - dw) / 2f, (ph - dh) / 2f, dw, dh)
            }
        }
        doc.save(outputFile)
        val count = doc.numberOfPages
        doc.close()
        return count
    }

    fun getPageCount(file: File): Int {
        PDDocument.load(file).use { return it.numberOfPages }
    }

    fun getMetadata(file: File): PdfMetaInfo {
        PDDocument.load(file).use { doc ->
            val info = doc.documentInformation
            return PdfMetaInfo(
                title = info?.title ?: "",
                author = info?.author ?: "",
                subject = info?.subject ?: "",
                pageCount = doc.numberOfPages
            )
        }
    }

    fun updateMetadata(file: File, title: String, author: String, subject: String) {
        val doc = PDDocument.load(file)
        doc.documentInformation.title = title
        doc.documentInformation.author = author
        doc.documentInformation.subject = subject
        saveInPlace(doc, file)
    }

    fun extractPageText(file: File, pageIndex: Int): String {
        return try {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper().apply {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }
                stripper.getText(doc).trim()
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun searchPagesContaining(file: File, query: String): List<Int> {
        if (query.isBlank()) return emptyList()
        val matches = mutableListOf<Int>()
        try {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                for (p in 1..doc.numberOfPages) {
                    stripper.startPage = p
                    stripper.endPage = p
                    val txt = stripper.getText(doc)
                    if (txt.contains(query, ignoreCase = true)) matches.add(p - 1)
                }
            }
        } catch (_: Exception) {}
        return matches
    }

    fun renderPageBitmap(file: File, pageIndex: Int, targetWidth: Int = 1150): Bitmap {
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

    // Commit all interactive DraftOverlays AND Freehand Ink Strokes permanently onto the PDF page!
    fun commitDraftsAndInk(file: File, pageIndex: Int, overlays: List<DraftOverlay>, strokes: List<InkStroke>) {
        if (overlays.isEmpty() && strokes.isEmpty()) return
        val doc = PDDocument.load(file)
        val page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
        val pw = page.mediaBox.width
        val ph = page.mediaBox.height

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            for (ov in overlays) {
                val x = (ov.xNorm.coerceIn(0.01f, 0.96f)) * pw
                val topY = ph - (ov.yNorm.coerceIn(0.01f, 0.96f)) * ph
                val boxW = (ov.wNorm.coerceIn(0.05f, 0.96f)) * pw
                val boxH = (ov.hNorm.coerceIn(0.02f, 0.96f)) * ph
                val bottomY = (topY - boxH).coerceAtLeast(4f)
                val rgb = ov.colorArgb.toInt()
                val cr = (rgb shr 16) and 0xFF
                val cg = (rgb shr 8) and 0xFF
                val cb = rgb and 0xFF

                when (ov.kind) {
                    OverlayKind.IMAGE -> {
                        if (ov.imageBitmap != null) {
                            val pdImg = LosslessFactory.createFromImage(doc, ov.imageBitmap)
                            cs.drawImage(pdImg, x, bottomY, boxW, boxH)
                        }
                    }
                    OverlayKind.WHITEOUT -> {
                        cs.setNonStrokingColor(255, 255, 255)
                        cs.addRect(x, bottomY, boxW, boxH)
                        cs.fill()
                    }
                    OverlayKind.REDACT -> {
                        cs.setNonStrokingColor(0, 0, 0)
                        cs.addRect(x, bottomY, boxW, boxH)
                        cs.fill()
                    }
                    OverlayKind.HIGHLIGHT -> {
                        cs.saveGraphicsState()
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.38f }
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(cr, cg, cb)
                        cs.addRect(x, bottomY, boxW, boxH)
                        cs.fill()
                        cs.restoreGraphicsState()
                    }
                    OverlayKind.SHAPE_RECT -> {
                        cs.setStrokingColor(cr, cg, cb)
                        cs.setLineWidth(2.0f)
                        cs.addRect(x, bottomY, boxW, boxH)
                        cs.stroke()
                    }
                    OverlayKind.SHAPE_LINE -> {
                        cs.setStrokingColor(cr, cg, cb)
                        cs.setLineWidth(2.4f)
                        cs.moveTo(x, topY - boxH / 2f)
                        cs.lineTo(x + boxW, topY - boxH / 2f)
                        cs.stroke()
                    }
                    OverlayKind.STICKY_NOTE, OverlayKind.TEXT -> {
                        if (ov.text.isNotBlank()) {
                            val font = resolveFont(ov.fontStyle, ov.isBold, ov.isItalic)
                            val fSize = (ov.fontSizeSp * 1.05f).coerceIn(8f, 54f)
                            val hasLink = ov.linkUrl.isNotBlank()
                            val tr = if (hasLink) 25 else cr
                            val tg = if (hasLink) 118 else cg
                            val tb = if (hasLink) 210 else cb

                            val lines = ov.text.split("\n")
                            var curY = (topY - fSize).coerceAtLeast(14f)

                            if (ov.kind == OverlayKind.STICKY_NOTE || ov.hasBgHighlight) {
                                val maxW = lines.maxOf { (font.getStringWidth(sanitize(it)) / 1000f) * fSize }.coerceAtLeast(60f)
                                val noteH = lines.size * fSize * 1.38f + 12f
                                cs.setNonStrokingColor(255, 245, 157)
                                cs.addRect(x - 5f, curY - noteH + fSize + 5f, maxW + 12f, noteH)
                                cs.fill()
                                if (ov.kind == OverlayKind.STICKY_NOTE) {
                                    cs.setStrokingColor(245, 166, 35)
                                    cs.setLineWidth(1.0f)
                                    cs.addRect(x - 5f, curY - noteH + fSize + 5f, maxW + 12f, noteH)
                                    cs.stroke()
                                }
                            }

                            cs.setNonStrokingColor(tr, tg, tb)
                            for (rawLine in lines) {
                                val clean = sanitize(rawLine)
                                cs.beginText()
                                cs.setFont(font, fSize)
                                cs.newLineAtOffset(x, curY)
                                cs.showText(clean)
                                cs.endText()

                                val w = ((font.getStringWidth(clean) / 1000f) * fSize).coerceAtLeast(24f)
                                if ((ov.isUnderline || hasLink) && clean.isNotEmpty()) {
                                    cs.setStrokingColor(tr, tg, tb)
                                    cs.setLineWidth(0.9f)
                                    cs.moveTo(x, curY - 2f)
                                    cs.lineTo(x + w, curY - 2f)
                                    cs.stroke()
                                }
                                if (ov.isStrikethrough && clean.isNotEmpty()) {
                                    cs.setStrokingColor(tr, tg, tb)
                                    cs.setLineWidth(0.9f)
                                    cs.moveTo(x, curY + fSize * 0.30f)
                                    cs.lineTo(x + w, curY + fSize * 0.30f)
                                    cs.stroke()
                                }
                                if (hasLink && clean.isNotEmpty()) {
                                    val finalUrl = if (ov.linkUrl.startsWith("http://") || ov.linkUrl.startsWith("https://")) ov.linkUrl else "https://${ov.linkUrl}"
                                    val link = PDAnnotationLink().apply {
                                        borderStyle = PDBorderStyleDictionary().apply { width = 0f }
                                        rectangle = PDRectangle(x, curY - 3f, w, fSize + 5f)
                                        action = PDActionURI().apply { uri = finalUrl }
                                    }
                                    page.annotations.add(link)
                                }
                                curY -= (fSize * 1.32f)
                            }
                        }
                    }
                }
            }

            // Commit Freehand Ink Drawing strokes
            for (st in strokes) {
                if (st.points.size < 2) continue
                val rgb = st.colorArgb.toInt()
                cs.setStrokingColor((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                cs.setLineWidth(st.strokeWidthPt)
                cs.setLineCapStyle(1)
                cs.setLineJoinStyle(1)
                val first = st.points.first()
                cs.moveTo(first.first * pw, ph - first.second * ph)
                for (i in 1 until st.points.size) {
                    val pt = st.points[i]
                    cs.lineTo(pt.first * pw, ph - pt.second * ph)
                }
                cs.stroke()
            }
        }
        saveInPlace(doc, file)
    }

    // --- PAGE OPERATIONS ---

    fun addBlankPage(file: File, afterPageIndex: Int, pageSize: String = "A4", landscape: Boolean = false): Int {
        val doc = PDDocument.load(file)
        val base = when (pageSize) {
            "Letter" -> PDRectangle.LETTER
            "Legal" -> PDRectangle.LEGAL
            else -> PDRectangle.A4
        }
        val rect = if (landscape) PDRectangle(base.height, base.width) else base
        val newPage = PDPage(rect)
        if (afterPageIndex in 0 until doc.numberOfPages - 1) {
            doc.pages.insertAfter(newPage, doc.getPage(afterPageIndex))
        } else {
            doc.addPage(newPage)
        }
        val count = doc.numberOfPages
        saveInPlace(doc, file)
        return count
    }

    fun rotatePage(file: File, pageIndex: Int, degrees: Int = 90, allPages: Boolean = false) {
        val doc = PDDocument.load(file)
        for (i in 0 until doc.numberOfPages) {
            if (allPages || i == pageIndex) {
                val p = doc.getPage(i)
                p.rotation = (p.rotation + degrees) % 360
            }
        }
        saveInPlace(doc, file)
    }

    fun deletePage(file: File, pageIndex: Int): Int {
        val doc = PDDocument.load(file)
        if (doc.numberOfPages <= 1) {
            doc.close()
            throw IllegalStateException("Cannot delete the only remaining page.")
        }
        doc.removePage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
        val count = doc.numberOfPages
        saveInPlace(doc, file)
        return count
    }

    fun movePage(file: File, fromIndex: Int, toIndex: Int) {
        val doc = PDDocument.load(file)
        val n = doc.numberOfPages
        if (fromIndex !in 0 until n || toIndex !in 0 until n || fromIndex == toIndex) {
            doc.close()
            return
        }
        val page = doc.getPage(fromIndex)
        doc.pages.remove(page)
        if (toIndex >= doc.numberOfPages) doc.addPage(page) else doc.pages.insertBefore(page, doc.getPage(toIndex))
        saveInPlace(doc, file)
    }

    fun addHeaderFooterNumbers(file: File, headerText: String = "", footerText: String = "", addNumbers: Boolean = true) {
        val doc = PDDocument.load(file)
        val total = doc.numberOfPages
        for (i in 0 until total) {
            val page = doc.getPage(i)
            val pw = page.mediaBox.width
            val ph = page.mediaBox.height
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.setNonStrokingColor(100, 100, 100)
                if (headerText.isNotBlank()) {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 9.5f)
                    cs.newLineAtOffset(36f, ph - 22f)
                    cs.showText(sanitize(headerText).take(85))
                    cs.endText()
                }
                if (footerText.isNotBlank()) {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 9.5f)
                    cs.newLineAtOffset(36f, 18f)
                    cs.showText(sanitize(footerText).take(65))
                    cs.endText()
                }
                if (addNumbers) {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 9.5f)
                    cs.newLineAtOffset(pw - 86f, 18f)
                    cs.showText("Page ${i + 1} / $total")
                    cs.endText()
                }
            }
        }
        saveInPlace(doc, file)
    }

    fun appendPdfIntoCurrent(currentFile: File, extraFile: File): Int {
        val tmpOut = File(currentFile.parentFile, "${currentFile.name}.merge.tmp")
        val merger = PDFMergerUtility()
        merger.destinationFileName = tmpOut.absolutePath
        merger.addSource(currentFile)
        merger.addSource(extraFile)
        merger.mergeDocuments(null)
        if (currentFile.exists()) currentFile.delete()
        tmpOut.renameTo(currentFile)
        currentFile.setLastModified(System.currentTimeMillis())
        return getPageCount(currentFile)
    }

    fun extractRangeToNewFile(srcFile: File, outFile: File, from1: Int, to1: Int): Int {
        PDDocument.load(srcFile).use { src ->
            val s = (from1 - 1).coerceIn(0, src.numberOfPages - 1)
            val e = (to1 - 1).coerceIn(s, src.numberOfPages - 1)
            val dst = PDDocument()
            for (i in s..e) dst.importPage(src.getPage(i))
            dst.save(outFile)
            val count = dst.numberOfPages
            dst.close()
            return count
        }
    }

    // --- DIGITAL SIGNATURE, WATERMARK, LINKS, SECURITY, COMPRESS, EXPORT ---

    fun stampDigitalSignature(file: File, pageIndex: Int, signerName: String, reason: String, location: String, xPercent: Float, yPercent: Float) {
        val hash = sha256Short(file)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val doc = PDDocument.load(file)
        val page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
        val pw = page.mediaBox.width
        val ph = page.mediaBox.height
        val boxW = 225f
        val boxH = 66f
        val x = (xPercent.coerceIn(0.04f, 0.62f) * pw).coerceAtMost((pw - boxW - 12f).coerceAtLeast(12f))
        val y = (ph - (yPercent.coerceIn(0.08f, 0.92f) * ph) - boxH).coerceAtLeast(16f)

        doc.documentInformation.setCustomMetadataValue("AuraDigitalSigner", signerName)
        doc.documentInformation.setCustomMetadataValue("AuraDigitalSignDate", timestamp)
        doc.documentInformation.setCustomMetadataValue("AuraDigitalSHA256", hash)

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setNonStrokingColor(244, 248, 255)
            cs.addRect(x, y, boxW, boxH)
            cs.fill()
            cs.setStrokingColor(25, 118, 210)
            cs.setLineWidth(1.2f)
            cs.addRect(x, y, boxW, boxH)
            cs.stroke()
            cs.setNonStrokingColor(25, 118, 210)
            cs.addRect(x, y + boxH - 15f, boxW, 15f)
            cs.fill()
            cs.setNonStrokingColor(255, 255, 255)
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 8f)
            cs.newLineAtOffset(x + 6f, y + boxH - 11f)
            cs.showText("DIGITALLY SIGNED • AURA VERIFIED")
            cs.endText()
            cs.setNonStrokingColor(20, 20, 20)
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 9.5f)
            cs.newLineAtOffset(x + 6f, y + boxH - 27f)
            cs.showText(sanitize("Signed by: ${signerName.ifBlank { "Authorized User" }}").take(38))
            cs.endText()
            cs.setNonStrokingColor(70, 70, 70)
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, 7.5f)
            cs.newLineAtOffset(x + 6f, y + boxH - 39f)
            cs.showText(sanitize("Date: $timestamp | ${location.ifBlank { "Local Device" }}").take(46))
            cs.endText()
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, 7.5f)
            cs.newLineAtOffset(x + 6f, y + boxH - 50f)
            cs.showText(sanitize("Reason: ${reason.ifBlank { "Document Approval" }}").take(46))
            cs.endText()
            cs.beginText()
            cs.setFont(PDType1Font.COURIER_BOLD, 7f)
            cs.newLineAtOffset(x + 6f, y + 6f)
            cs.showText("SHA-256: $hash")
            cs.endText()
        }
        saveInPlace(doc, file)
    }

    fun addCustomWatermark(file: File, pageIndex: Int, text: String, fontSize: Float, opacity: Float, angleDegrees: Float, colorHex: Int, allPages: Boolean) {
        val clean = sanitize(text.ifBlank { "CONFIDENTIAL" })
        val r = (colorHex shr 16) and 0xFF
        val g = (colorHex shr 8) and 0xFF
        val b = colorHex and 0xFF
        val doc = PDDocument.load(file)
        for (i in 0 until doc.numberOfPages) {
            if (!allPages && i != pageIndex) continue
            val page = doc.getPage(i)
            val pw = page.mediaBox.width
            val ph = page.mediaBox.height
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = opacity.coerceIn(0.08f, 1.0f) }
                cs.setGraphicsStateParameters(gs)
                cs.setNonStrokingColor(r, g, b)
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize.coerceIn(14f, 84f))
                cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(angleDegrees.toDouble()), pw * 0.20f, ph * 0.42f))
                cs.showText(clean)
                cs.endText()
            }
        }
        saveInPlace(doc, file)
    }

    fun listLinks(file: File): List<PdfLinkInfo> {
        val result = mutableListOf<PdfLinkInfo>()
        PDDocument.load(file).use { doc ->
            for (p in 0 until doc.numberOfPages) {
                doc.getPage(p).annotations.forEachIndexed { idx, ann ->
                    if (ann is PDAnnotationLink) {
                        val uri = (ann.action as? PDActionURI)?.uri ?: "(Internal Link)"
                        result.add(PdfLinkInfo(idx, p, uri))
                    }
                }
            }
        }
        return result
    }

    fun removeLinks(file: File, targetPage: Int? = null, targetUrl: String? = null): Int {
        var removed = 0
        val doc = PDDocument.load(file)
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
        saveInPlace(doc, file)
        return removed
    }

    fun protectPdf(file: File, password: String) {
        val doc = PDDocument.load(file)
        val spp = StandardProtectionPolicy(password, password, AccessPermission()).apply { encryptionKeyLength = 128 }
        doc.protect(spp)
        saveInPlace(doc, file)
    }

    fun unlockPdf(file: File, password: String) {
        val doc = PDDocument.load(file, password)
        doc.isAllSecurityToBeRemoved = true
        saveInPlace(doc, file)
    }

    fun compressPdf(srcFile: File, outFile: File, jpegQuality: Float = 0.60f, widthPx: Int = 950) {
        val count = getPageCount(srcFile)
        val dst = PDDocument()
        for (i in 0 until count) {
            val bmp = renderPageBitmap(srcFile, i, widthPx)
            val page = PDPage(PDRectangle.A4)
            dst.addPage(page)
            val pdImg = JPEGFactory.createFromImage(dst, bmp, jpegQuality)
            PDPageContentStream(dst, page).use { cs ->
                cs.drawImage(pdImg, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
            }
            bmp.recycle()
        }
        dst.save(outFile)
        dst.close()
    }

    fun imagesToPdf(context: Context, uris: List<Uri>, outputFile: File): Int {
        val doc = PDDocument()
        for (uri in uris) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return@use
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val pw = page.mediaBox.width
                val ph = page.mediaBox.height
                val margin = 16f
                val scale = minOf((pw - margin * 2) / bmp.width, (ph - margin * 2) / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.88f)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImg, (pw - dw) / 2f, (ph - dh) / 2f, dw, dh)
                }
                bmp.recycle()
            }
        }
        doc.save(outputFile)
        val count = doc.numberOfPages
        doc.close()
        return count
    }

    fun exportPageAsPng(file: File, pageIndex: Int, outPng: File) {
        val bmp = renderPageBitmap(file, pageIndex, 1440)
        FileOutputStream(outPng).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 95, out) }
        bmp.recycle()
    }
}
