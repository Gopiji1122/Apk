package com.aurapdf.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.DocumentsContract
import com.aurapdf.editor.data.PdfRepository
import com.aurapdf.editor.model.CompressConfig
import com.aurapdf.editor.model.CompressionResult
import com.aurapdf.editor.model.ExportFormat
import com.aurapdf.editor.model.FreehandStroke
import com.aurapdf.editor.model.OverlayItem
import com.aurapdf.editor.model.PageRangeMode
import com.aurapdf.editor.model.PdfFontFamily
import com.aurapdf.editor.model.PdfLinkInfo
import com.aurapdf.editor.model.SearchMatch
import com.aurapdf.editor.model.ShapeKind
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.io.MemoryUsageSetting
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
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

class PdfBoxEngine(
    private val context: Context,
    private val repository: PdfRepository
) {

    private fun openPdDocument(file: File, password: String?): PDDocument {
        return if (!password.isNullOrEmpty()) {
            PDDocument.load(file, password)
        } else {
            PDDocument.load(file)
        }
    }

    private fun modifyDocumentAtomically(
        file: File,
        password: String?,
        block: (PDDocument) -> Unit
    ) {
        val tempOut = File(
            context.cacheDir,
            "atomic_${System.currentTimeMillis()}_${file.name.hashCode()}.pdf"
        )
        try {
            openPdDocument(file, password).use { doc ->
                block(doc)
                doc.save(tempOut)
            }
            tempOut.copyTo(file, overwrite = true)
        } finally {
            if (tempOut.exists()) tempOut.delete()
        }
    }

    private fun resolveRenderableFile(file: File, password: String?): File {
        if (password.isNullOrEmpty()) return file
        val tempUnlocked = File(context.cacheDir, "unlocked_${file.name.hashCode()}_${file.lastModified()}_${file.length()}.pdf")
        if (tempUnlocked.exists() && tempUnlocked.length() > 0L) return tempUnlocked
        openPdDocument(file, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(tempUnlocked)
        }
        return tempUnlocked
    }

    suspend fun checkIsPasswordLocked(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file).use { doc ->
                doc.isEncrypted
            }
        } catch (_: Exception) {
            true
        }
    }

    suspend fun verifyPassword(file: File, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file, password).use { true }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getPageCount(file: File, password: String? = null): Int = withContext(Dispatchers.IO) {
        try {
            val target = resolveRenderableFile(file, password)
            val pfd = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (_: Exception) {
            openPdDocument(file, password).use { it.numberOfPages }
        }
    }

    suspend fun renderPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 1280,
        password: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val target = resolveRenderableFile(file, password)
            val pfd = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val safeIdx = pageIndex.coerceIn(0, max(0, renderer.pageCount - 1))
            val page = renderer.openPage(safeIdx)
            val scale = targetWidthPx.toFloat() / max(1, page.width).toFloat()
            val outW = max(1, (page.width * scale).toInt())
            val outH = max(1, (page.height * scale).toInt())
            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bmp
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractAllPagesText(file: File, password: String? = null): List<String> = withContext(Dispatchers.IO) {
        try {
            openPdDocument(file, password).use { doc ->
                val count = doc.numberOfPages
                val stripper = PDFTextStripper()
                (1..count).map { pageNum ->
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    runCatching { stripper.getText(doc) }.getOrDefault("")
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun searchInPages(
        pagesText: List<String>,
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean
    ): List<SearchMatch> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val escaped = Regex.escape(trimmed)
        val pattern = if (wholeWord) "\\b$escaped\\b" else escaped
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val regex = Regex(pattern, options)

        val matches = mutableListOf<SearchMatch>()
        pagesText.forEachIndexed { pageIdx, text ->
            var localCount = 0
            regex.findAll(text).forEach { mr ->
                val start = max(0, mr.range.first - 28)
                val end = min(text.length, mr.range.last + 36)
                val rawSnippet = text.substring(start, end).replace('\n', ' ').replace('\r', ' ').trim()
                val prefix = if (start > 0) "..." else ""
                val suffix = if (end < text.length) "..." else ""
                matches.add(
                    SearchMatch(
                        pageIndex = pageIdx,
                        matchIndexOnPage = localCount++,
                        snippet = "$prefix$rawSnippet$suffix"
                    )
                )
            }
        }
        return matches
    }

    suspend fun runOfflineOcrOnPage(
        file: File,
        pageIndex: Int,
        password: String? = null
    ): String = withContext(Dispatchers.IO) {
        val bmp = renderPageBitmap(file, pageIndex, targetWidthPx = 1600, password = password)
            ?: throw IllegalStateException("Unable to render page for OCR")
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bmp, 0)
            val visionText = recognizer.process(inputImage).await()
            visionText.text.ifBlank { "No text recognized on Page ${pageIndex + 1}." }
        } finally {
            bmp.recycle()
        }
    }

    fun createSnapshot(file: File): File {
        val snap = File(repository.snapshotsDir, "snap_${file.name.hashCode()}_${System.currentTimeMillis()}.pdf")
        file.copyTo(snap, overwrite = true)
        return snap
    }

    suspend fun restoreSnapshot(targetFile: File, snapshotFile: File) = withContext(Dispatchers.IO) {
        if (snapshotFile.exists()) {
            snapshotFile.copyTo(targetFile, overwrite = true)
            repository.renderPage1Thumbnail(targetFile, forceRefresh = true)
        }
    }

    suspend fun saveEditsToPdf(
        file: File,
        overlays: List<OverlayItem>,
        strokes: List<FreehandStroke>,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        val snapshot = createSnapshot(file)
        modifyDocumentAtomically(file, password) { doc ->
            val pageCount = doc.numberOfPages
            for (pageIdx in 0 until pageCount) {
                val pageOverlays = overlays.filter { it.pageIndex == pageIdx }
                val pageStrokes = strokes.filter { it.pageIndex == pageIdx }
                if (pageOverlays.isEmpty() && pageStrokes.isEmpty()) continue

                val page = doc.getPage(pageIdx)
                val box = page.cropBox ?: page.mediaBox
                val pageW = box.width
                val pageH = box.height
                val originX = box.lowerLeftX
                val originY = box.lowerLeftY

                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    for (stroke in pageStrokes) {
                        if (stroke.points.size < 2) continue
                        val (r, g, b) = argbToRgb(stroke.colorArgb)
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth((stroke.strokeWidthNorm * pageW).coerceIn(1f, 24f))
                        val first = stroke.points.first()
                        cs.moveTo(originX + first.x * pageW, originY + (1f - first.y) * pageH)
                        for (pt in stroke.points.drop(1)) {
                            cs.lineTo(originX + pt.x * pageW, originY + (1f - pt.y) * pageH)
                        }
                        cs.stroke()
                    }

                    for (item in pageOverlays) {
                        val left = originX + item.xNorm * pageW
                        val width = item.wNorm * pageW
                        val height = item.hNorm * pageH
                        val bottom = originY + (1f - (item.yNorm + item.hNorm)) * pageH
                        val top = bottom + height

                        when (item) {
                            is OverlayItem.WhiteoutOverlay -> {
                                cs.setNonStrokingColor(255, 255, 255)
                                cs.addRect(left, bottom, width, height)
                                cs.fill()
                            }

                            is OverlayItem.RedactOverlay -> {
                                cs.setNonStrokingColor(12, 12, 14)
                                cs.addRect(left, bottom, width, height)
                                cs.fill()
                                if (item.label.isNotBlank()) {
                                    cs.beginText()
                                    cs.setFont(PDType1Font.HELVETICA_BOLD, 9f)
                                    cs.setNonStrokingColor(255, 255, 255)
                                    cs.newLineAtOffset(left + 6f, bottom + height / 2f - 3f)
                                    cs.showText(sanitizeWinAnsi(item.label))
                                    cs.endText()
                                }
                            }

                            is OverlayItem.HighlightOverlay -> {
                                val gs = PDExtendedGraphicsState().apply {
                                    nonStrokingAlphaConstant = item.opacity.coerceIn(0.1f, 0.85f)
                                }
                                cs.saveGraphicsState()
                                cs.setGraphicsStateParameters(gs)
                                val (r, g, b) = argbToRgb(item.colorArgb)
                                cs.setNonStrokingColor(r, g, b)
                                cs.addRect(left, bottom, width, height)
                                cs.fill()
                                cs.restoreGraphicsState()
                            }

                            is OverlayItem.ShapeOverlay -> {
                                val (r, g, b) = argbToRgb(item.colorArgb)
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(item.strokeWidthPt.coerceIn(1f, 16f))
                                when (item.shapeKind) {
                                    ShapeKind.RECTANGLE -> {
                                        cs.addRect(left, bottom, width, height)
                                        cs.stroke()
                                    }
                                    ShapeKind.LINE -> {
                                        cs.moveTo(left, bottom)
                                        cs.lineTo(left + width, top)
                                        cs.stroke()
                                    }
                                }
                            }

                            is OverlayItem.ImageOverlay -> {
                                val imgFile = File(item.imagePath)
                                if (imgFile.exists()) {
                                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                                    if (bmp != null) {
                                        val pdImg = LosslessFactory.createFromImage(doc, bmp)
                                        cs.drawImage(pdImg, left, bottom, width, height)
                                        bmp.recycle()
                                    }
                                }
                            }

                            is OverlayItem.SignatureOverlay -> {
                                val sigBmp = renderSignatureBitmap(item, 900, 320)
                                val pdImg = LosslessFactory.createFromImage(doc, sigBmp)
                                cs.drawImage(pdImg, left, bottom, width, height)
                                sigBmp.recycle()
                            }

                            is OverlayItem.StickyNoteOverlay -> {
                                val (r, g, b) = argbToRgb(item.colorArgb)
                                cs.setNonStrokingColor(r, g, b)
                                cs.addRect(left, bottom, min(28f, width), min(28f, height))
                                cs.fill()
                                cs.setStrokingColor(90, 70, 10)
                                cs.setLineWidth(1f)
                                cs.addRect(left, bottom, min(28f, width), min(28f, height))
                                cs.stroke()

                                val noteAnnot = PDAnnotationText().apply {
                                    rectangle = PDRectangle(left, bottom, 28f, 28f)
                                    contents = item.noteText
                                    titlePopup = item.author
                                    name = PDAnnotationText.NAME_COMMENT
                                }
                                page.annotations.add(noteAnnot)
                            }

                            is OverlayItem.TextOverlay -> {
                                if (item.highlightColorArgb != null) {
                                    val gs = PDExtendedGraphicsState().apply {
                                        nonStrokingAlphaConstant = 0.40f
                                    }
                                    cs.saveGraphicsState()
                                    cs.setGraphicsStateParameters(gs)
                                    val (hr, hg, hb) = argbToRgb(item.highlightColorArgb)
                                    cs.setNonStrokingColor(hr, hg, hb)
                                    cs.addRect(left, bottom, width, height)
                                    cs.fill()
                                    cs.restoreGraphicsState()
                                }

                                val pdFont = resolvePdFont(item.fontFamily, item.isBold, item.isItalic)
                                val fontSize = item.fontSizeSp.coerceIn(7f, 72f)
                                val (r, g, b) = argbToRgb(item.colorArgb)
                                if (item.text.trim() == "✓") {
                                    cs.setStrokingColor(r, g, b)
                                    cs.setLineWidth(max(2.2f, fontSize * 0.12f))
                                    val cx1 = left + width * 0.15f
                                    val cy1 = bottom + height * 0.45f
                                    val cx2 = left + width * 0.42f
                                    val cy2 = bottom + height * 0.18f
                                    val cx3 = left + width * 0.85f
                                    val cy3 = bottom + height * 0.82f
                                    cs.moveTo(cx1, cy1)
                                    cs.lineTo(cx2, cy2)
                                    cs.lineTo(cx3, cy3)
                                    cs.stroke()
                                    continue
                                }
                                val cleanText = sanitizeWinAnsi(item.text)
                                val lines = cleanText.split("\n")
                                val lineHeight = fontSize * 1.25f

                                lines.forEachIndexed { idx, line ->
                                    val lineY = top - fontSize - (idx * lineHeight)
                                    if (lineY >= bottom - 4f) {
                                        cs.beginText()
                                        cs.setFont(pdFont, fontSize)
                                        cs.setNonStrokingColor(r, g, b)
                                        cs.newLineAtOffset(left + 2f, lineY)
                                        cs.showText(line)
                                        cs.endText()

                                        val textWidth = try {
                                            pdFont.getStringWidth(line) / 1000f * fontSize
                                        } catch (_: Exception) {
                                            line.length * fontSize * 0.52f
                                        }
                                        if (item.isUnderline) {
                                            cs.setStrokingColor(r, g, b)
                                            cs.setLineWidth(max(0.8f, fontSize * 0.06f))
                                            cs.moveTo(left + 2f, lineY - 2f)
                                            cs.lineTo(left + 2f + textWidth, lineY - 2f)
                                            cs.stroke()
                                        }
                                        if (item.isStrikethrough) {
                                            cs.setStrokingColor(r, g, b)
                                            cs.setLineWidth(max(0.8f, fontSize * 0.06f))
                                            cs.moveTo(left + 2f, lineY + fontSize * 0.32f)
                                            cs.lineTo(left + 2f + textWidth, lineY + fontSize * 0.32f)
                                            cs.stroke()
                                        }
                                    }
                                }

                                if (!item.linkUrl.isNullOrBlank()) {
                                    val normalizedUri = if (item.linkUrl.startsWith("http://") ||
                                        item.linkUrl.startsWith("https://") ||
                                        item.linkUrl.startsWith("mailto:")
                                    ) item.linkUrl else "https://${item.linkUrl}"
                                    val linkAnnot = PDAnnotationLink().apply {
                                        rectangle = PDRectangle(left, bottom, width, height)
                                        action = PDActionURI().apply { uri = normalizedUri }
                                        borderStyle = PDBorderStyleDictionary().apply { width = 0f }
                                    }
                                    page.annotations.add(linkAnnot)
                                }
                            }
                        }
                    }
                }
            }
            
        }
        repository.renderPage1Thumbnail(file, forceRefresh = true)
        snapshot
    }

    fun renderSignatureBitmap(sig: OverlayItem.SignatureOverlay, wPx: Int, hPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = sig.inkColorArgb.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val sigAreaH = if (sig.digitalCertInfo != null) hPx * 0.68f else hPx.toFloat()
        for (stroke in sig.strokes) {
            if (stroke.size < 2) continue
            val path = Path()
            path.moveTo(stroke[0].x * wPx, stroke[0].y * sigAreaH)
            for (pt in stroke.drop(1)) {
                path.lineTo(pt.x * wPx, pt.y * sigAreaH)
            }
            canvas.drawPath(path, paint)
        }

        sig.digitalCertInfo?.let { cert ->
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8E24AA.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x188E24AA
                style = Paint.Style.FILL
            }
            val topY = hPx * 0.68f
            canvas.drawRect(4f, topY, wPx - 4f, hPx - 4f, bgPaint)
            canvas.drawRect(4f, topY, wPx - 4f, hPx - 4f, borderPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF222222.toInt()
                textSize = 20f
            }
            canvas.drawText(
                "Digitally signed by ${cert.signerName} | ${cert.timestamp}",
                16f,
                topY + 28f,
                textPaint
            )
            canvas.drawText(
                "Reason: ${cert.reason} | Loc: ${cert.location} | SHA-256: ${cert.sha256Hash.take(18)}...",
                16f,
                topY + 56f,
                textPaint
            )
        }
        return bmp
    }

    private fun resolvePdFont(family: PdfFontFamily, bold: Boolean, italic: Boolean): PDType1Font {
        return when (family) {
            PdfFontFamily.SANS -> when {
                bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                bold -> PDType1Font.HELVETICA_BOLD
                italic -> PDType1Font.HELVETICA_OBLIQUE
                else -> PDType1Font.HELVETICA
            }
            PdfFontFamily.SERIF -> when {
                bold && italic -> PDType1Font.TIMES_BOLD_ITALIC
                bold -> PDType1Font.TIMES_BOLD
                italic -> PDType1Font.TIMES_ITALIC
                else -> PDType1Font.TIMES_ROMAN
            }
            PdfFontFamily.MONO -> when {
                bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE
                bold -> PDType1Font.COURIER_BOLD
                italic -> PDType1Font.COURIER_OBLIQUE
                else -> PDType1Font.COURIER
            }
        }
    }

    private fun argbToRgb(argb: Long): Triple<Int, Int, Int> {
        val c = argb.toInt()
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return Triple(r, g, b)
    }

    private fun sanitizeWinAnsi(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '\u2018', '\u2019' -> sb.append('\'')
                '\u201C', '\u201D' -> sb.append('"')
                '\u2013', '\u2014' -> sb.append('-')
                '\u2022' -> sb.append('*')
                '\u2026' -> sb.append("...")
                '\t' -> sb.append("    ")
                else -> if (ch.code in 32..255) sb.append(ch) else sb.append(' ')
            }
        }
        return sb.toString()
    }

    // === ORGANIZE TOOLS ===

    suspend fun organizeAddBlankPage(file: File, insertAfterIndex: Int, password: String? = null) =
        withContext(Dispatchers.IO) {
            modifyDocumentAtomically(file, password) { doc ->
                val refSize = if (doc.numberOfPages > 0) {
                    doc.getPage(insertAfterIndex.coerceIn(0, doc.numberOfPages - 1)).mediaBox
                } else {
                    PDRectangle.A4
                }
                val newPage = PDPage(refSize)
                if (insertAfterIndex + 1 >= doc.numberOfPages) {
                    doc.addPage(newPage)
                } else {
                    val targetPage = doc.getPage((insertAfterIndex + 1).coerceIn(0, doc.numberOfPages - 1))
                    doc.pages.insertBefore(newPage, targetPage)
                }
                
            }
            repository.renderPage1Thumbnail(file, forceRefresh = true)
        }

    suspend fun organizeMergePdf(file: File, otherPdfUri: Uri, password: String? = null) =
        withContext(Dispatchers.IO) {
            val tempOther = File(context.cacheDir, "merge_in_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(otherPdfUri)?.use { input ->
                FileOutputStream(tempOther).use { output -> input.copyTo(output) }
            }
            val merger = PDFMergerUtility()
            val outTemp = File(context.cacheDir, "merge_out_${System.currentTimeMillis()}.pdf")
            merger.destinationFileName = outTemp.absolutePath
            merger.addSource(resolveRenderableFile(file, password))
            merger.addSource(tempOther)
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            outTemp.copyTo(file, overwrite = true)
            tempOther.delete()
            outTemp.delete()
            repository.renderPage1Thumbnail(file, forceRefresh = true)
        }

    suspend fun organizeRotatePage(file: File, pageIndex: Int, deltaDegrees: Int, password: String? = null) =
        withContext(Dispatchers.IO) {
            modifyDocumentAtomically(file, password) { doc ->
                val idx = pageIndex.coerceIn(0, doc.numberOfPages - 1)
                val page = doc.getPage(idx)
                val current = page.rotation
                page.rotation = ((current + deltaDegrees) % 360 + 360) % 360
                
            }
            repository.renderPage1Thumbnail(file, forceRefresh = true)
        }

    suspend fun organizeMovePage(file: File, fromIndex: Int, toIndex: Int, password: String? = null) =
        withContext(Dispatchers.IO) {
            modifyDocumentAtomically(file, password) { doc ->
                val count = doc.numberOfPages
                if (fromIndex !in 0 until count || toIndex !in 0 until count || fromIndex == toIndex) return@modifyDocumentAtomically
                val moved = doc.getPage(fromIndex)
                val target = doc.getPage(toIndex)
                doc.pages.remove(moved)
                if (fromIndex > toIndex) {
                    doc.pages.insertBefore(moved, target)
                } else {
                    doc.pages.insertAfter(moved, target)
                }
            }
            repository.renderPage1Thumbnail(file, forceRefresh = true)
        }

    suspend fun organizeDeletePage(file: File, pageIndex: Int, password: String? = null) =
        withContext(Dispatchers.IO) {
            modifyDocumentAtomically(file, password) { doc ->
                if (doc.numberOfPages <= 1) {
                    throw IllegalStateException("Cannot delete the only remaining page")
                }
                val idx = pageIndex.coerceIn(0, doc.numberOfPages - 1)
                doc.removePage(idx)
                
            }
            repository.renderPage1Thumbnail(file, forceRefresh = true)
        }

    suspend fun organizeSplitRange(
        file: File,
        rangeSpec: String,
        outputTitle: String,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(repository.pdfsDir, "${outputTitle.removeSuffix(".pdf")}.pdf")
        openPdDocument(file, password).use { srcDoc ->
            val indices = parsePageRange(rangeSpec, srcDoc.numberOfPages)
            if (indices.isEmpty()) throw IllegalArgumentException("No valid pages in range: $rangeSpec")
            PDDocument().use { dstDoc ->
                for (idx in indices) {
                    dstDoc.importPage(srcDoc.getPage(idx))
                }
                dstDoc.save(outFile)
            }
        }
        repository.renderPage1Thumbnail(outFile, forceRefresh = true)
        outFile
    }

    suspend fun organizeCropMargins(
        file: File,
        pageIndex: Int,
        leftPct: Float,
        topPct: Float,
        rightPct: Float,
        bottomPct: Float,
        applyAll: Boolean,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        modifyDocumentAtomically(file, password) { doc ->
            val indices = if (applyAll) (0 until doc.numberOfPages).toList() else listOf(pageIndex)
            for (idx in indices) {
                val page = doc.getPage(idx)
                val mb = page.mediaBox
                val l = (leftPct / 100f).coerceIn(0f, 0.4f) * mb.width
                val r = (rightPct / 100f).coerceIn(0f, 0.4f) * mb.width
                val t = (topPct / 100f).coerceIn(0f, 0.4f) * mb.height
                val b = (bottomPct / 100f).coerceIn(0f, 0.4f) * mb.height
                page.cropBox = PDRectangle(
                    mb.lowerLeftX + l,
                    mb.lowerLeftY + b,
                    max(50f, mb.width - l - r),
                    max(50f, mb.height - t - b)
                )
            }
            
        }
        repository.renderPage1Thumbnail(file, forceRefresh = true)
    }

    suspend fun organizeAddHeaderFooter(
        file: File,
        headerText: String,
        footerText: String,
        includePageNumbers: Boolean,
        fontSize: Float,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        modifyDocumentAtomically(file, password) { doc ->
            val total = doc.numberOfPages
            for (idx in 0 until total) {
                val page = doc.getPage(idx)
                val box = page.cropBox ?: page.mediaBox
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    cs.setFont(PDType1Font.HELVETICA, fontSize.coerceIn(8f, 20f))
                    cs.setNonStrokingColor(80, 85, 95)
                    if (headerText.isNotBlank()) {
                        cs.beginText()
                        cs.newLineAtOffset(box.lowerLeftX + 36f, box.upperRightY - 26f)
                        cs.showText(sanitizeWinAnsi(headerText))
                        cs.endText()
                    }
                    val combinedFooter = buildString {
                        if (footerText.isNotBlank()) append(footerText)
                        if (includePageNumbers) {
                            if (isNotEmpty()) append("   |   ")
                            append("Page ${idx + 1} of $total")
                        }
                    }
                    if (combinedFooter.isNotBlank()) {
                        cs.beginText()
                        cs.newLineAtOffset(box.lowerLeftX + 36f, box.lowerLeftY + 18f)
                        cs.showText(sanitizeWinAnsi(combinedFooter))
                        cs.endText()
                    }
                }
            }
            
        }
        repository.renderPage1Thumbnail(file, forceRefresh = true)
    }

    // === LINKS, WATERMARK, SECURITY, PROPERTIES ===

    suspend fun getComments(file: File, password: String? = null): List<Triple<Int, String, String>> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<Triple<Int, String, String>>()
            runCatching {
                openPdDocument(file, password).use { doc ->
                    for (pIdx in 0 until doc.numberOfPages) {
                        doc.getPage(pIdx).annotations.forEach { annot ->
                            if (annot is PDAnnotationText && !annot.contents.isNullOrBlank()) {
                                result.add(
                                    Triple(
                                        pIdx,
                                        annot.titlePopup ?: "Aura User",
                                        annot.contents ?: ""
                                    )
                                )
                            }
                        }
                    }
                }
            }
            result
        }

    suspend fun getLinks(file: File, password: String? = null): List<PdfLinkInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PdfLinkInfo>()
        openPdDocument(file, password).use { doc ->
            for (pIdx in 0 until doc.numberOfPages) {
                val annots = doc.getPage(pIdx).annotations
                annots.forEachIndexed { aIdx, annot ->
                    if (annot is PDAnnotationLink) {
                        val uri = (annot.action as? PDActionURI)?.uri ?: "(Internal Action)"
                        val r = annot.rectangle
                        result.add(
                            PdfLinkInfo(
                                pageIndex = pIdx,
                                annotationIndex = aIdx,
                                uri = uri,
                                rectSummary = "Page ${pIdx + 1} (${r.lowerLeftX.toInt()}, ${r.lowerLeftY.toInt()})"
                            )
                        )
                    }
                }
            }
        }
        result
    }

    suspend fun addLink(
        file: File,
        pageIndex: Int,
        xNorm: Float,
        yNorm: Float,
        wNorm: Float,
        hNorm: Float,
        uri: String,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        modifyDocumentAtomically(file, password) { doc ->
            val page = doc.getPage(pageIndex.coerceIn(0, doc.numberOfPages - 1))
            val box = page.cropBox ?: page.mediaBox
            val left = box.lowerLeftX + xNorm * box.width
            val bottom = box.lowerLeftY + (1f - yNorm - hNorm) * box.height
            val normalizedUri = if (uri.startsWith("http://") || uri.startsWith("https://")) uri else "https://$uri"
            val annot = PDAnnotationLink().apply {
                rectangle = PDRectangle(left, bottom, wNorm * box.width, hNorm * box.height)
                action = PDActionURI().apply { this.uri = normalizedUri }
            }
            page.annotations.add(annot)
            
        }
    }

    suspend fun removeLink(file: File, pageIndex: Int, annotationIndex: Int, password: String? = null) =
        withContext(Dispatchers.IO) {
            modifyDocumentAtomically(file, password) { doc ->
                val annots = doc.getPage(pageIndex).annotations
                if (annotationIndex in annots.indices) {
                    annots.removeAt(annotationIndex)
                }
                
            }
        }

    suspend fun removeAllLinks(file: File, password: String? = null) = withContext(Dispatchers.IO) {
        modifyDocumentAtomically(file, password) { doc ->
            for (pIdx in 0 until doc.numberOfPages) {
                val annots = doc.getPage(pIdx).annotations
                annots.removeAll { it is PDAnnotationLink }
            }
            
        }
    }

    suspend fun applyWatermark(
        file: File,
        text: String,
        fontSize: Float,
        opacity: Float,
        angleDeg: Float,
        colorArgb: Long,
        allPages: Boolean,
        currentPage: Int,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        modifyDocumentAtomically(file, password) { doc ->
            val targets = if (allPages) (0 until doc.numberOfPages).toList() else listOf(currentPage)
            val (r, g, b) = argbToRgb(colorArgb)
            val cleanText = sanitizeWinAnsi(text)
            for (idx in targets) {
                val page = doc.getPage(idx)
                val box = page.cropBox ?: page.mediaBox
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 0.90f)
                    }
                    cs.saveGraphicsState()
                    cs.setGraphicsStateParameters(gs)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize.coerceIn(14f, 120f))
                    cs.setNonStrokingColor(r, g, b)
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val cx = box.lowerLeftX + box.width * 0.28f
                    val cy = box.lowerLeftY + box.height * 0.42f
                    val matrix = Matrix.getRotateInstance(rad, cx, cy)
                    cs.setTextMatrix(matrix)
                    cs.showText(cleanText)
                    cs.endText()
                    cs.restoreGraphicsState()
                }
            }
            
        }
        repository.renderPage1Thumbnail(file, forceRefresh = true)
    }

    suspend fun setPasswordProtection(file: File, newPassword: String, currentPassword: String? = null) =
        withContext(Dispatchers.IO) {
            modifyDocumentAtomically(file, currentPassword) { doc ->
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(newPassword, newPassword, ap).apply {
                    encryptionKeyLength = 128
                    permissions = ap
                }
                doc.protect(spp)
                
            }
        }

    suspend fun removePasswordProtection(file: File, currentPassword: String) =
        withContext(Dispatchers.IO) {
            modifyDocumentAtomically(file, currentPassword) { doc ->
                doc.isAllSecurityToBeRemoved = true
                
            }
            repository.renderPage1Thumbnail(file, forceRefresh = true)
        }

    suspend fun getDocumentProperties(file: File, password: String? = null): Map<String, String> =
        withContext(Dispatchers.IO) {
            openPdDocument(file, password).use { doc ->
                val info = doc.documentInformation
                mapOf(
                    "title" to (info.title ?: ""),
                    "author" to (info.author ?: ""),
                    "subject" to (info.subject ?: ""),
                    "keywords" to (info.keywords ?: "")
                )
            }
        }

    suspend fun updateDocumentProperties(
        file: File,
        title: String,
        author: String,
        subject: String,
        keywords: String,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        modifyDocumentAtomically(file, password) { doc ->
            val info = doc.documentInformation
            info.title = title
            info.author = author
            info.subject = subject
            info.keywords = keywords
            info.creator = "Aura PDF Editor"
            doc.documentInformation = info
            
        }
    }

    // === ADVANCED COMPRESS PDF ===

    suspend fun compressPdf(
        file: File,
        config: CompressConfig,
        replaceOriginal: Boolean,
        password: String? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalBytes = file.length()
        val renderable = resolveRenderableFile(file, password)
        val pfd = ParcelFileDescriptor.open(renderable, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        val outFile = if (replaceOriginal) {
            File(context.cacheDir, "compressed_tmp_${System.currentTimeMillis()}.pdf")
        } else {
            File(repository.pdfsDir, "${file.nameWithoutExtension}_Compressed.pdf")
        }

        val scaleFactor = (config.targetDpi.coerceIn(72, 300) / 72f)
        val qualityFloat = (config.jpegQuality.coerceIn(15, 95) / 100f)

        PDDocument().use { outDoc ->
            for (idx in 0 until renderer.pageCount) {
                val page = renderer.openPage(idx)
                val ptW = page.width.toFloat()
                val ptH = page.height.toFloat()
                val pxW = max(120, (ptW * scaleFactor).toInt())
                val pxH = max(120, (ptH * scaleFactor).toInt())
                val bmp = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val finalBmp = if (config.grayscale) {
                    val gray = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
                    val c = Canvas(gray)
                    val p = Paint().apply {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                    }
                    c.drawBitmap(bmp, 0f, 0f, p)
                    bmp.recycle()
                    gray
                } else {
                    bmp
                }

                val newPage = PDPage(PDRectangle(ptW, ptH))
                outDoc.addPage(newPage)
                val pdImg = JPEGFactory.createFromImage(outDoc, finalBmp, qualityFloat)
                PDPageContentStream(outDoc, newPage).use { cs ->
                    cs.drawImage(pdImg, 0f, 0f, ptW, ptH)
                }
                finalBmp.recycle()
            }

            if (!config.stripMetadata) {
                runCatching {
                    openPdDocument(file, password).use { origDoc ->
                        outDoc.documentInformation = origDoc.documentInformation
                    }
                }
            }
            outDoc.save(outFile)
        }
        renderer.close()
        pfd.close()

        val finalTarget = if (replaceOriginal) {
            outFile.copyTo(file, overwrite = true)
            outFile.delete()
            file
        } else {
            outFile
        }
        repository.renderPage1Thumbnail(finalTarget, forceRefresh = true)
        val compressedBytes = finalTarget.length()
        val pct = if (originalBytes > 0L) {
            ((originalBytes - compressedBytes).toFloat() / originalBytes.toFloat() * 100f)
        } else 0f

        CompressionResult(
            outputFilePath = finalTarget.absolutePath,
            originalBytes = originalBytes,
            compressedBytes = compressedBytes,
            reductionPercent = pct
        )
    }

    // === ADVANCED EXPORT PDF ===

    suspend fun exportPdf(
        file: File,
        pageRangeMode: PageRangeMode,
        customRange: String,
        currentPage: Int,
        format: ExportFormat,
        jpegQuality: Int,
        dpi: Int,
        bundleAsZip: Boolean,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        val totalPages = getPageCount(file, password)
        val selectedPages = when (pageRangeMode) {
            PageRangeMode.CURRENT -> listOf(currentPage.coerceIn(0, totalPages - 1))
            PageRangeMode.CUSTOM -> parsePageRange(customRange, totalPages).ifEmpty { listOf(0) }
            PageRangeMode.ALL -> (0 until totalPages).toList()
        }
        val baseName = file.nameWithoutExtension

        when (format) {
            ExportFormat.PNG, ExportFormat.JPEG -> {
                val targetWidthPx = ((dpi.coerceIn(72, 300) / 72f) * 595f).toInt()
                val renderedFiles = mutableListOf<File>()
                for (pIdx in selectedPages) {
                    val bmp = renderPageBitmap(file, pIdx, targetWidthPx, password) ?: continue
                    val ext = if (format == ExportFormat.PNG) "png" else "jpg"
                    val imgFile = File(repository.exportsDir, "${baseName}_page_${pIdx + 1}.$ext")
                    FileOutputStream(imgFile).use { out ->
                        if (format == ExportFormat.PNG) {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } else {
                            bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(20, 100), out)
                        }
                    }
                    bmp.recycle()
                    renderedFiles.add(imgFile)
                }
                if (renderedFiles.size > 1 || bundleAsZip) {
                    val zipFile = File(repository.exportsDir, "${baseName}_pages.zip")
                    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                        for (rf in renderedFiles) {
                            zos.putNextEntry(ZipEntry(rf.name))
                            FileInputStream(rf).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                    zipFile
                } else {
                    renderedFiles.firstOrNull()
                        ?: throw IllegalStateException("No pages rendered for export")
                }
            }

            ExportFormat.TXT -> {
                val allText = extractAllPagesText(file, password)
                val outTxt = File(repository.exportsDir, "$baseName.txt")
                val combined = selectedPages.joinToString("\n\n--- Page Break ---\n\n") { idx ->
                    "=== Page ${idx + 1} ===\n${allText.getOrElse(idx) { "" }.trim()}"
                }
                outTxt.writeText(combined, Charsets.UTF_8)
                outTxt
            }

            ExportFormat.HTML -> {
                val allText = extractAllPagesText(file, password)
                val outHtml = File(repository.exportsDir, "$baseName.html")
                val body = selectedPages.joinToString("\n") { idx ->
                    val escaped = allText.getOrElse(idx) { "" }
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\n", "<br/>\n")
                    "<section style=\"margin-bottom:32px;padding:24px;border:1px solid #ddd;border-radius:8px;\">" +
                        "<h3 style=\"color:#E52521;font-family:sans-serif;\">Page ${idx + 1}</h3>" +
                        "<p style=\"font-family:sans-serif;line-height:1.6;\">$escaped</p></section>"
                }
                outHtml.writeText("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>$baseName</title></head><body>$body</body></html>")
                outHtml
            }

            ExportFormat.DOCX -> {
                val allText = extractAllPagesText(file, password)
                val outDocx = File(repository.exportsDir, "$baseName.docx")
                buildSimpleDocx(outDocx, selectedPages.map { idx -> allText.getOrElse(idx) { "" } })
                outDocx
            }

            ExportFormat.PDF_COPY -> {
                val outPdf = File(repository.exportsDir, "${baseName}_export.pdf")
                openPdDocument(file, password).use { srcDoc ->
                    PDDocument().use { dstDoc ->
                        for (idx in selectedPages) {
                            dstDoc.importPage(srcDoc.getPage(idx))
                        }
                        dstDoc.save(outPdf)
                    }
                }
                outPdf
            }
        }
    }

    private fun buildSimpleDocx(destFile: File, pageTexts: List<String>) {
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()

        val rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()

        val parasXml = buildString {
            pageTexts.forEachIndexed { pageIdx, text ->
                text.lines().forEach { line ->
                    val esc = line
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    append("<w:p><w:r><w:t xml:space=\"preserve\">$esc</w:t></w:r></w:p>")
                }
                if (pageIdx < pageTexts.lastIndex) {
                    append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
            }
        }

        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>$parasXml</w:body>
            </w:document>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypes.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(rels.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    fun parsePageRange(spec: String, totalPages: Int): List<Int> {
        val result = linkedSetOf<Int>()
        for (part in spec.split(",")) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if ("-" in trimmed) {
                val bounds = trimmed.split("-", limit = 2)
                val start = (bounds[0].trim().toIntOrNull() ?: 1).coerceIn(1, totalPages)
                val end = (bounds[1].trim().toIntOrNull() ?: totalPages).coerceIn(1, totalPages)
                val step = if (start <= end) 1 else -1
                for (p in start..end step step) {
                    result.add(p - 1)
                }
            } else {
                val p = trimmed.toIntOrNull()
                if (p != null && p in 1..totalPages) {
                    result.add(p - 1)
                }
            }
        }
        return result.toList()
    }


    suspend fun exportPdfImagesToFolder(
        file: File,
        pageRangeMode: PageRangeMode,
        customRange: String,
        currentPage: Int,
        format: ExportFormat,
        jpegQuality: Int,
        dpi: Int,
        treeUri: Uri,
        password: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val totalPages = getPageCount(file, password)
        val selectedPages = when (pageRangeMode) {
            PageRangeMode.CURRENT -> listOf(currentPage.coerceIn(0, totalPages - 1))
            PageRangeMode.CUSTOM -> parsePageRange(customRange, totalPages).ifEmpty { listOf(0) }
            PageRangeMode.ALL -> (0 until totalPages).toList()
        }
        val baseName = file.nameWithoutExtension
        val targetWidthPx = ((dpi.coerceIn(72, 300) / 72f) * 595f).toInt()
        val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val ext = if (format == ExportFormat.PNG) "png" else "jpg"
        val mime = if (format == ExportFormat.PNG) "image/png" else "image/jpeg"
        var writtenCount = 0

        for (pIdx in selectedPages) {
            val bmp = renderPageBitmap(file, pIdx, targetWidthPx, password) ?: continue
            try {
                val displayName = "${baseName}_page_${pIdx + 1}.$ext"
                val childUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    mime,
                    displayName
                )
                if (childUri != null) {
                    context.contentResolver.openOutputStream(childUri)?.use { out ->
                        if (format == ExportFormat.PNG) {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } else {
                            bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(20, 100), out)
                        }
                    }
                    writtenCount++
                }
            } finally {
                bmp.recycle()
            }
        }
        writtenCount
    }

    // === NATIVE ANDROID PRINTMANAGER ===

    fun printPdf(activityContext: Context, file: File, password: String? = null) {
        val renderable = runCatching { resolveRenderableFile(file, password) }.getOrDefault(file)
        val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(renderable).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(file.nameWithoutExtension, adapter, null)
    }
}
