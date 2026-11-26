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
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

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

    // ---------------------------------------------------------------
    // v2.0 additions (all offline)
    // ---------------------------------------------------------------

    /** Page numbers: position 0 = left, 1 = center, 2 = right; style 0 = "1", 1 = "Page 1", 2 = "Page 1 of N" */
    suspend fun addPageNumbers(inputFile: File, position: Int, style: Int, outputFile: File): File =
        withContext(Dispatchers.IO) {
            PDDocument.load(inputFile).use { doc ->
                val total = doc.numberOfPages
                val font = PDType1Font.HELVETICA
                val size = 10f
                for ((i, page) in doc.pages.withIndex()) {
                    val box = page.cropBox ?: page.mediaBox
                    val label = when (style) {
                        1 -> "Page ${i + 1}"
                        2 -> "Page ${i + 1} of $total"
                        else -> "${i + 1}"
                    }
                    val w = font.getStringWidth(label) / 1000f * size
                    val x = when (position) {
                        0 -> box.lowerLeftX + 36f
                        2 -> box.lowerLeftX + box.width - w - 36f
                        else -> box.lowerLeftX + (box.width - w) / 2f
                    }
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.beginText()
                        cs.setFont(font, size)
                        cs.setNonStrokingColor(90, 90, 90)
                        cs.newLineAtOffset(x, box.lowerLeftY + 22f)
                        cs.showText(label)
                        cs.endText()
                    }
                }
                doc.save(outputFile)
            }
            outputFile
        }

    /** Big rotated status stamp (APPROVED, DRAFT, ...) on every page. rgb = packed 0xRRGGBB */
    suspend fun addStamp(inputFile: File, text: String, rgb: Int, outputFile: File): File =
        withContext(Dispatchers.IO) {
            PDDocument.load(inputFile).use { doc ->
                val font = PDType1Font.HELVETICA_BOLD
                val size = 54f
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                val gs = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = 0.55f
                }
                for (page in doc.pages) {
                    val box = page.cropBox ?: page.mediaBox
                    val w = font.getStringWidth(text) / 1000f * size
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(r, g, b)
                        cs.beginText()
                        cs.setFont(font, size)
                        cs.setTextMatrix(
                            Matrix.getRotateInstance(
                                Math.toRadians(20.0),
                                box.lowerLeftX + (box.width - w * 0.94f) / 2f,
                                box.lowerLeftY + box.height * 0.55f
                            )
                        )
                        cs.showText(text)
                        cs.endText()
                    }
                }
                doc.save(outputFile)
            }
            outputFile
        }

    /** Trim every page by the given margins (PDF points) */
    suspend fun cropPages(
        inputFile: File, left: Float, top: Float, right: Float, bottom: Float, outputFile: File
    ): File = withContext(Dispatchers.IO) {
        PDDocument.load(inputFile).use { doc ->
            for (page in doc.pages) {
                val box = page.mediaBox
                val newW = box.width - left - right
                val newH = box.height - top - bottom
                if (newW > 50f && newH > 50f) {
                    page.cropBox = PDRectangle(box.lowerLeftX + left, box.lowerLeftY + bottom, newW, newH)
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    /** Insert a blank page after the given 1-based page (0 = at the very start) */
    suspend fun insertBlankPage(inputFile: File, afterPage: Int, outputFile: File): File =
        withContext(Dispatchers.IO) {
            PDDocument.load(inputFile).use { doc ->
                val refIndex = (afterPage - 1).coerceIn(0, doc.numberOfPages - 1)
                val ref = doc.getPage(refIndex)
                val blank = PDPage(PDRectangle(ref.mediaBox.width, ref.mediaBox.height))
                if (afterPage <= 0) doc.pages.insertBefore(blank, doc.getPage(0))
                else doc.pages.insertAfter(blank, ref)
                doc.save(outputFile)
            }
            outputFile
        }

    /** Burn form fields into the page so they can no longer be edited */
    suspend fun flattenForms(inputFile: File, outputFile: File): File =
        withContext(Dispatchers.IO) {
            PDDocument.load(inputFile).use { doc ->
                doc.documentCatalog.acroForm?.flatten()
                doc.save(outputFile)
            }
            outputFile
        }

    /** Plain text of the whole document (embedded text only) */
    suspend fun extractText(inputFile: File): String = withContext(Dispatchers.IO) {
        PDDocument.load(inputFile).use { doc -> PDFTextStripper().getText(doc) }
    }

    /** TXT, DOCX (text) and XLSX (cell values) to a clean paginated PDF */
    suspend fun officeToPdf(context: Context, uri: Uri, ext: String, outputFile: File): File =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot read selected file")
            val lines: List<String> = input.use {
                when (ext) {
                    "docx" -> readDocxParagraphs(it)
                    "xlsx" -> readXlsxRows(it)
                    else -> it.bufferedReader().readLines()
                }
            }
            layoutTextPdf(lines, outputFile)
            outputFile
        }

    private fun readZipEntries(input: InputStream, wanted: Set<String>): Map<String, ByteArray> {
        val map = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name in wanted) map[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun readDocxParagraphs(input: InputStream): List<String> {
        val bytes = readZipEntries(input, setOf("word/document.xml"))["word/document.xml"]
            ?: throw IllegalStateException("Not a valid DOCX file")
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val paragraphs = ArrayList<String>()
        val current = StringBuilder()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name.substringAfter(':') == "t") inText = true
                XmlPullParser.TEXT -> if (inText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "t" -> inText = false
                    "p" -> { paragraphs.add(current.toString()); current.setLength(0) }
                }
            }
            event = parser.next()
        }
        return paragraphs
    }

    private fun readXlsxRows(input: InputStream): List<String> {
        val entries = readZipEntries(input, setOf("xl/sharedStrings.xml", "xl/worksheets/sheet1.xml"))
        val shared = ArrayList<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), null)
            val sb = StringBuilder()
            var inT = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "t") inT = true
                    XmlPullParser.TEXT -> if (inT) sb.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "t" -> inT = false
                        "si" -> { shared.add(sb.toString()); sb.setLength(0) }
                    }
                }
                event = parser.next()
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: throw IllegalStateException("Not a valid XLSX file")
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(sheet), null)
        val rows = ArrayList<String>()
        val cells = ArrayList<String>()
        var cellType: String? = null
        var inV = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> cellType = parser.getAttributeValue(null, "t")
                    "v" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) {
                    val raw = parser.text
                    cells.add(if (cellType == "s") shared.getOrElse(raw.toIntOrNull() ?: -1) { "" } else raw)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "row" -> { rows.add(cells.joinToString("   |   ")); cells.clear() }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun layoutTextPdf(paragraphs: List<String>, outputFile: File) {
        PDDocument().use { doc ->
            val font = PDType1Font.HELVETICA
            val size = 11f
            val lead = 15f
            val margin = 54f
            val box = PDRectangle.A4
            val maxWidth = box.width - margin * 2
            var page = PDPage(box)
            doc.addPage(page)
            var cs = PDPageContentStream(doc, page)
            var y = box.height - margin

            fun drawLine(text: String) {
                if (y < margin) {
                    cs.close()
                    page = PDPage(box)
                    doc.addPage(page)
                    cs = PDPageContentStream(doc, page)
                    y = box.height - margin
                }
                if (text.isNotEmpty()) {
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.newLineAtOffset(margin, y)
                    cs.showText(text)
                    cs.endText()
                }
                y -= lead
            }

            for (para in paragraphs) {
                val clean = para.map { if (it.code in 32..255) it else ' ' }.joinToString("")
                if (clean.isBlank()) { drawLine(""); continue }
                var line = ""
                for (word in clean.split(' ')) {
                    val test = if (line.isEmpty()) word else "$line $word"
                    if (font.getStringWidth(test) / 1000f * size > maxWidth && line.isNotEmpty()) {
                        drawLine(line)
                        line = word
                    } else line = test
                }
                if (line.isNotEmpty()) drawLine(line)
            }
            cs.close()
            doc.save(outputFile)
        }
    }
}
