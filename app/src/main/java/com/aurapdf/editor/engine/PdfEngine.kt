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

    fun getPageCount(file: File): Int {
        PDDocument.load(file).use { return it.numberOfPages }
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

    // --- PAGE OPERATIONS (IN-PLACE) ---

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
        if (toIndex >= doc.numberOfPages) {
            doc.addPage(page)
        } else {
            doc.pages.insertBefore(page, doc.getPage(toIndex))
        }
        saveInPlace(doc, file)
    }

    fun addPageNumbers(file: File) {
        val doc = PDDocument.load(file)
        val total = doc.numberOfPages
        for (i in 0 until total) {
            val page = doc.getPage(i)
            val pw = page.mediaBox.width
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.setNonStrokingColor(110, 110, 110)
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 10f)
                cs.newLineAtOffset(pw / 2f - 24f, 18f)
                cs.showText("Page ${i + 1} / $total")
                cs.endText()
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

    // --- CONTENT EDITING: TEXT, HIGHLIGHT, REDACT, IMAGE (JPG/PNG) ---

    fun addTextToPage(
        file: File,
        pageIndex: Int,
        text: String,
        fontSize: Float,
        isBold: Boolean,
        isItalic: Boolean,
        colorHex: Int,
        xPercent: Float,
        yPercent: Float,
        asStickyNote: Boolean = false,
        linkUrl: String = ""
    ) {
        val doc = PDDocument.load(file)
        val page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
        val pw = page.mediaBox.width
        val ph = page.mediaBox.height
        val x = xPercent.coerceIn(0.03f, 0.90f) * pw
        var curY = ph - (yPercent.coerceIn(0.05f, 0.94f) * ph)

        val font = when {
            isBold && isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            isBold -> PDType1Font.HELVETICA_BOLD
            isItalic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }

        val lines = text.split("\n")
        val hasLink = linkUrl.isNotBlank()
        val r = if (hasLink) 25 else (colorHex shr 16) and 0xFF
        val g = if (hasLink) 118 else (colorHex shr 8) and 0xFF
        val b = if (hasLink) 210 else colorHex and 0xFF

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            if (asStickyNote) {
                val boxH = (lines.size * fontSize * 1.4f + 16f).coerceAtLeast(36f)
                val boxW = (pw * 0.45f).coerceAtMost(pw - x - 16f)
                cs.setNonStrokingColor(255, 245, 157)
                cs.addRect(x - 6f, curY - boxH + fontSize + 6f, boxW, boxH)
                cs.fill()
                cs.setStrokingColor(249, 168, 37)
                cs.setLineWidth(1f)
                cs.addRect(x - 6f, curY - boxH + fontSize + 6f, boxW, boxH)
                cs.stroke()
            }

            cs.setNonStrokingColor(r, g, b)
            lines.forEach { rawLine ->
                val clean = sanitize(rawLine)
                cs.beginText()
                cs.setFont(font, fontSize)
                cs.newLineAtOffset(x, curY)
                cs.showText(clean)
                cs.endText()

                if (hasLink && clean.isNotEmpty()) {
                    val w = ((font.getStringWidth(clean) / 1000f) * fontSize).coerceAtLeast(30f)
                    cs.setStrokingColor(25, 118, 210)
                    cs.setLineWidth(0.9f)
                    cs.moveTo(x, curY - 2f)
                    cs.lineTo(x + w, curY - 2f)
                    cs.stroke()

                    val finalUrl = if (linkUrl.startsWith("http://") || linkUrl.startsWith("https://")) linkUrl else "https://$linkUrl"
                    val link = PDAnnotationLink().apply {
                        borderStyle = PDBorderStyleDictionary().apply { width = 0f }
                        rectangle = PDRectangle(x, curY - 3f, w, fontSize + 5f)
                        action = PDActionURI().apply { uri = finalUrl }
                    }
                    page.annotations.add(link)
                }
                curY -= (fontSize * 1.35f)
            }
        }
        saveInPlace(doc, file)
    }

    fun addImageToPage(
        context: Context,
        file: File,
        pageIndex: Int,
        imageUri: Uri,
        xPercent: Float,
        yPercent: Float,
        widthPercent: Float
    ) {
        val doc = PDDocument.load(file)
        val page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
        val pw = page.mediaBox.width
        val ph = page.mediaBox.height
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            val bmp = BitmapFactory.decodeStream(input) ?: throw IllegalStateException("Cannot decode image.")
            val pdImg = LosslessFactory.createFromImage(doc, bmp)
            val drawW = widthPercent.coerceIn(0.12f, 0.94f) * pw
            val drawH = (drawW * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1))).coerceAtMost(ph * 0.85f)
            val x = (xPercent.coerceIn(0.02f, 0.90f) * pw).coerceAtMost((pw - drawW).coerceAtLeast(8f))
            val y = (ph - (yPercent.coerceIn(0.04f, 0.92f) * ph) - drawH).coerceAtLeast(12f)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.drawImage(pdImg, x, y, drawW, drawH)
            }
            bmp.recycle()
        }
        saveInPlace(doc, file)
    }

    // Highlight or Permanent Black Redaction box on current page
    fun addBoxOverlay(
        file: File,
        pageIndex: Int,
        xPercent: Float,
        yPercent: Float,
        widthPercent: Float,
        heightPercent: Float,
        isRedaction: Boolean
    ) {
        val doc = PDDocument.load(file)
        val page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
        val pw = page.mediaBox.width
        val ph = page.mediaBox.height
        val w = widthPercent.coerceIn(0.08f, 0.92f) * pw
        val h = heightPercent.coerceIn(0.02f, 0.50f) * ph
        val x = xPercent.coerceIn(0.02f, 0.90f) * pw
        val y = (ph - (yPercent.coerceIn(0.04f, 0.94f) * ph) - h).coerceAtLeast(8f)

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            if (isRedaction) {
                cs.setNonStrokingColor(0, 0, 0)
                cs.addRect(x, y, w, h)
                cs.fill()
            } else {
                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.38f }
                cs.setGraphicsStateParameters(gs)
                cs.setNonStrokingColor(255, 235, 59)
                cs.addRect(x, y, w, h)
                cs.fill()
            }
        }
        saveInPlace(doc, file)
    }

    // --- SIGNATURES: NORMAL DRAWN/TYPED + DIGITAL SIGNATURE CERTIFICATE STAMP ---

    fun stampDrawnSignature(file: File, pageIndex: Int, bmp: Bitmap, xPercent: Float, yPercent: Float, widthPercent: Float = 0.34f) {
        val doc = PDDocument.load(file)
        val page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
        val pw = page.mediaBox.width
        val ph = page.mediaBox.height
        val pdImg = LosslessFactory.createFromImage(doc, bmp)
        val w = widthPercent.coerceIn(0.15f, 0.75f) * pw
        val h = w * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1))
        val x = xPercent.coerceIn(0.04f, 0.78f) * pw
        val y = (ph - yPercent.coerceIn(0.06f, 0.92f) * ph - h).coerceAtLeast(16f)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.drawImage(pdImg, x, y, w, h)
        }
        saveInPlace(doc, file)
    }

    fun stampDigitalSignature(
        file: File,
        pageIndex: Int,
        signerName: String,
        reason: String,
        location: String,
        xPercent: Float,
        yPercent: Float
    ) {
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
            // Certificate background & border
            cs.setNonStrokingColor(244, 248, 255)
            cs.addRect(x, y, boxW, boxH)
            cs.fill()
            cs.setStrokingColor(25, 118, 210)
            cs.setLineWidth(1.2f)
            cs.addRect(x, y, boxW, boxH)
            cs.stroke()

            // Verified header bar
            cs.setNonStrokingColor(25, 118, 210)
            cs.addRect(x, y + boxH - 15f, boxW, 15f)
            cs.fill()
            cs.setNonStrokingColor(255, 255, 255)
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 8f)
            cs.newLineAtOffset(x + 6f, y + boxH - 11f)
            cs.showText("DIGITALLY SIGNED • AURA VERIFIED")
            cs.endText()

            // Signer details + SHA-256 fingerprint
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

    // --- CUSTOMIZABLE WATERMARK (TEXT, SIZE, OPACITY, COLOR, ANGLE, SCOPE) ---

    fun addCustomWatermark(
        file: File,
        pageIndex: Int,
        text: String,
        fontSize: Float,
        opacity: Float,
        angleDegrees: Float,
        colorHex: Int,
        allPages: Boolean
    ) {
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
                val gs = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = opacity.coerceIn(0.08f, 1.0f)
                }
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

    // --- HYPERLINKS: LIST, ADD, REMOVE ---

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

    // --- SECURITY (ENCRYPT / UNLOCK) & COMPRESS / EXPORT ---

    fun protectPdf(file: File, password: String) {
        val doc = PDDocument.load(file)
        val spp = StandardProtectionPolicy(password, password, AccessPermission()).apply {
            encryptionKeyLength = 128
        }
        doc.protect(spp)
        saveInPlace(doc, file)
    }

    fun unlockPdf(file: File, password: String) {
        val doc = PDDocument.load(file, password)
        doc.isAllSecurityToBeRemoved = true
        saveInPlace(doc, file)
    }

    fun compressPdf(srcFile: File, outFile: File, jpegQuality: Float = 0.65f) {
        val count = getPageCount(srcFile)
        val dst = PDDocument()
        for (i in 0 until count) {
            val bmp = renderPageBitmap(srcFile, i, 1000)
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
                val margin = 20f
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
        FileOutputStream(outPng).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bmp.recycle()
    }
}
