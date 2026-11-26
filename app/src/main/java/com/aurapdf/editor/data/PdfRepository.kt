package com.aurapdf.editor.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Xml
import com.aurapdf.editor.model.AppThemeMode
import com.aurapdf.editor.model.BookmarkItem
import com.aurapdf.editor.model.PageOrientation
import com.aurapdf.editor.model.PageSizeOption
import com.aurapdf.editor.model.RecentPdfItem
import com.aurapdf.editor.model.ScanFilter
import com.aurapdf.editor.model.ScannedPageDraft
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.sqrt

class PdfRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("aura_pdf_prefs", Context.MODE_PRIVATE)
    val pdfsDir: File = File(context.filesDir, "pdfs").apply { mkdirs() }
    val thumbnailsDir: File = File(context.filesDir, "thumbnails").apply { mkdirs() }
    val exportsDir: File = File(context.filesDir, "exports").apply { mkdirs() }
    val snapshotsDir: File = File(context.cacheDir, "snapshots").apply { mkdirs() }

    fun getThemeMode(): AppThemeMode {
        val raw = prefs.getString("theme_mode", AppThemeMode.ACROBAT_DARK.name)
        return runCatching { AppThemeMode.valueOf(raw ?: AppThemeMode.ACROBAT_DARK.name) }
            .getOrDefault(AppThemeMode.ACROBAT_DARK)
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    private fun getStarredSet(): MutableSet<String> {
        return prefs.getStringSet("starred_paths", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun toggleStar(filePath: String): Boolean {
        val set = getStarredSet()
        val nowStarred = if (set.contains(filePath)) {
            set.remove(filePath)
            false
        } else {
            set.add(filePath)
            true
        }
        prefs.edit().putStringSet("starred_paths", set).apply()
        return nowStarred
    }

    suspend fun loadRecentPdfs(): List<RecentPdfItem> = withContext(Dispatchers.IO) {
        val starred = getStarredSet()
        val files = pdfsDir.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        files.map { file ->
            val info = inspectPdfQuick(file)
            renderPage1Thumbnail(file, forceRefresh = false)
            RecentPdfItem(
                id = file.absolutePath.hashCode().toString(),
                name = file.name,
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                isStarred = starred.contains(file.absolutePath),
                pageCount = info.first,
                isEncrypted = info.second
            )
        }
    }

    private fun inspectPdfQuick(file: File): Pair<Int, Boolean> {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            Pair(count, false)
        } catch (_: SecurityException) {
            Pair(1, true)
        } catch (_: Exception) {
            try {
                PDDocument.load(file).use { doc ->
                    Pair(doc.numberOfPages, doc.isEncrypted)
                }
            } catch (_: Exception) {
                Pair(1, true)
            }
        }
    }

    fun getThumbnailFile(pdfFile: File): File {
        val key = "${pdfFile.name}_${pdfFile.lastModified()}_${pdfFile.length()}".hashCode().toString()
        return File(thumbnailsDir, "thumb_$key.png")
    }

    fun renderPage1Thumbnail(pdfFile: File, forceRefresh: Boolean = false): File? {
        val thumbFile = getThumbnailFile(pdfFile)
        if (!forceRefresh && thumbFile.exists() && thumbFile.length() > 0L) {
            return thumbFile
        }
        thumbnailsDir.listFiles()?.filter { it.name.startsWith("thumb_") && it != thumbFile }
            ?.takeIf { it.size > 120 }
            ?.forEach { it.delete() }

        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val targetW = 240
                val scale = targetW.toFloat() / max(1, page.width)
                val targetH = max(1, (page.height * scale).toInt())
                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                bmp.recycle()
            }
            renderer.close()
            pfd.close()
            if (thumbFile.exists()) thumbFile else null
        } catch (_: Exception) {
            null
        }
    }

    fun resolveDisplayName(uri: Uri, fallback: String): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun uniquePdfFile(desiredName: String): File {
        val cleanBase = desiredName
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .trim()
            .ifBlank { "Document_${System.currentTimeMillis()}" }
        var candidate = File(pdfsDir, "$cleanBase.pdf")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(pdfsDir, "${cleanBase}_($counter).pdf")
            counter++
        }
        return candidate
    }

    suspend fun importPdfFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val name = resolveDisplayName(uri, "Imported_${System.currentTimeMillis()}.pdf")
        val destFile = uniquePdfFile(name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read selected PDF")
        renderPage1Thumbnail(destFile, forceRefresh = true)
        destFile
    }

    suspend fun createBlankPdf(
        title: String,
        sizeOption: PageSizeOption,
        orientation: PageOrientation
    ): File = withContext(Dispatchers.IO) {
        val destFile = uniquePdfFile(title)
        val w = if (orientation == PageOrientation.PORTRAIT) sizeOption.widthPt else sizeOption.heightPt
        val h = if (orientation == PageOrientation.PORTRAIT) sizeOption.heightPt else sizeOption.widthPt
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(w, h))
            doc.addPage(page)
            doc.save(destFile)
        }
        renderPage1Thumbnail(destFile, forceRefresh = true)
        destFile
    }

    suspend fun imagesToPdf(uris: List<Uri>, title: String): File = withContext(Dispatchers.IO) {
        val destFile = uniquePdfFile(title)
        PDDocument().use { doc ->
            for (uri in uris) {
                val bmp = decodeSampledBitmap(uri, 2200) ?: continue
                val pageW = 595f
                val aspect = bmp.height.toFloat() / max(1, bmp.width).toFloat()
                val pageH = (pageW * aspect).coerceIn(300f, 1400f)
                val page = PDPage(PDRectangle(pageW, pageH))
                doc.addPage(page)
                val pdImage = JPEGFactory.createFromImage(doc, bmp, 0.88f)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, 0f, 0f, pageW, pageH)
                }
                bmp.recycle()
            }
            if (doc.numberOfPages == 0) {
                doc.addPage(PDPage(PDRectangle.A4))
            }
            doc.save(destFile)
        }
        renderPage1Thumbnail(destFile, forceRefresh = true)
        destFile
    }

    suspend fun scannedPagesToPdf(drafts: List<ScannedPageDraft>, title: String): File = withContext(Dispatchers.IO) {
        val destFile = uniquePdfFile(title)
        PDDocument().use { doc ->
            for (draft in drafts) {
                val srcFile = File(draft.imagePath)
                if (!srcFile.exists()) continue
                val rawBmp = BitmapFactory.decodeFile(srcFile.absolutePath) ?: continue
                val processed = applyScanPipeline(rawBmp, draft)
                val pageW = 595f
                val aspect = processed.height.toFloat() / max(1, processed.width).toFloat()
                val pageH = (pageW * aspect).coerceIn(300f, 1400f)
                val page = PDPage(PDRectangle(pageW, pageH))
                doc.addPage(page)
                val pdImage = JPEGFactory.createFromImage(doc, processed, 0.90f)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, 0f, 0f, pageW, pageH)
                }
                if (processed !== rawBmp) processed.recycle()
                rawBmp.recycle()
            }
            if (doc.numberOfPages == 0) {
                doc.addPage(PDPage(PDRectangle.A4))
            }
            doc.save(destFile)
        }
        renderPage1Thumbnail(destFile, forceRefresh = true)
        destFile
    }

    fun applyScanPipeline(source: Bitmap, draft: ScannedPageDraft): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val srcPts = floatArrayOf(
            draft.topLeft.x * w, draft.topLeft.y * h,
            draft.topRight.x * w, draft.topRight.y * h,
            draft.bottomRight.x * w, draft.bottomRight.y * h,
            draft.bottomLeft.x * w, draft.bottomLeft.y * h
        )
        val outW = max(200, ((dist(srcPts[0], srcPts[1], srcPts[2], srcPts[3]) +
                dist(srcPts[6], srcPts[7], srcPts[4], srcPts[5])) / 2f).toInt())
        val outH = max(200, ((dist(srcPts[0], srcPts[1], srcPts[6], srcPts[7]) +
                dist(srcPts[2], srcPts[3], srcPts[4], srcPts[5])) / 2f).toInt())

        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )
        val perspectiveMatrix = Matrix()
        perspectiveMatrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val warped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val warpCanvas = Canvas(warped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = buildScanColorFilter(draft.filter)
        warpCanvas.drawBitmap(source, perspectiveMatrix, paint)

        val normalizedRot = ((draft.rotationDeg % 360) + 360) % 360
        if (normalizedRot == 0) return warped
        val rotMatrix = Matrix().apply { postRotate(normalizedRot.toFloat()) }
        val rotated = Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, rotMatrix, true)
        warped.recycle()
        return rotated
    }

    private fun buildScanColorFilter(filter: ScanFilter): ColorMatrixColorFilter? {
        return when (filter) {
            ScanFilter.ORIGINAL -> null
            ScanFilter.AUTO_COLOR -> {
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -18f,
                        0f, 1.25f, 0f, 0f, -18f,
                        0f, 0f, 1.25f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorMatrixColorFilter(cm)
            }
            ScanFilter.GRAYSCALE -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                ColorMatrixColorFilter(cm)
            }
            ScanFilter.BW_CRISP -> {
                val sat = ColorMatrix().apply { setSaturation(0f) }
                val contrast = ColorMatrix(
                    floatArrayOf(
                        2.1f, 0f, 0f, 0f, -110f,
                        0f, 2.1f, 0f, 0f, -110f,
                        0f, 0f, 2.1f, 0f, -110f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                sat.postConcat(contrast)
                ColorMatrixColorFilter(sat)
            }
            ScanFilter.LIGHTEN -> {
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.15f, 0f, 0f, 0f, 28f,
                        0f, 1.15f, 0f, 0f, 28f,
                        0f, 0f, 1.15f, 0f, 28f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorMatrixColorFilter(cm)
            }
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun decodeSampledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    }

    /**
     * 100% Offline Word (.docx) & Excel (.xlsx / .csv) to PDF Converter (Pure XML/ZIP parser)
     */
    suspend fun officeOrCsvToPdf(uri: Uri): File = withContext(Dispatchers.IO) {
        val rawName = resolveDisplayName(uri, "Converted_Document")
        val lower = rawName.lowercase()
        val baseTitle = rawName.substringBeforeLast('.')
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open selected file")

        when {
            lower.endsWith(".docx") -> convertDocxBytesToPdf(bytes, baseTitle)
            lower.endsWith(".xlsx") -> convertXlsxBytesToPdf(bytes, baseTitle)
            lower.endsWith(".csv") -> convertCsvBytesToPdf(bytes, baseTitle)
            else -> {
                if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                    val entries = readZipEntries(bytes)
                    if (entries.containsKey("word/document.xml")) {
                        convertDocxBytesToPdf(bytes, baseTitle)
                    } else if (entries.any { it.key.startsWith("xl/") }) {
                        convertXlsxBytesToPdf(bytes, baseTitle)
                    } else {
                        convertCsvBytesToPdf(bytes, baseTitle)
                    }
                } else {
                    convertCsvBytesToPdf(bytes, baseTitle)
                }
            }
        }
    }

    private fun readZipEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(ByteArrayInputStream(zipBytes))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    map[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return map
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

    private data class DocxBlock(
        val text: String,
        val isHeading: Boolean = false,
        val isBold: Boolean = false,
        val tableRow: List<String>? = null
    )

    private fun convertDocxBytesToPdf(bytes: ByteArray, title: String): File {
        val entries = readZipEntries(bytes)
        val docXml = entries["word/document.xml"]
            ?: throw IllegalArgumentException("Invalid .docx archive: missing word/document.xml")

        val blocks = mutableListOf<DocxBlock>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(docXml), "UTF-8")

        var inParagraph = false
        var inTableCell = false
        var isHeading = false
        var isBold = false
        val paraBuilder = StringBuilder()
        var currentRow = mutableListOf<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when {
                        tag.endsWith("tr") -> currentRow = mutableListOf()
                        tag.endsWith("tc") -> inTableCell = true
                        tag.endsWith("p") -> {
                            inParagraph = true
                            isHeading = false
                            isBold = false
                            paraBuilder.clear()
                        }
                        tag.endsWith("pStyle") -> {
                            val styleVal = parser.getAttributeValue(null, "w:val")
                                ?: parser.getAttributeValue(null, "val") ?: ""
                            if (styleVal.contains("Heading", ignoreCase = true) ||
                                styleVal.contains("Title", ignoreCase = true)
                            ) {
                                isHeading = true
                            }
                        }
                        tag.endsWith("b") -> isBold = true
                        tag.endsWith("tab") -> if (inParagraph) paraBuilder.append("    ")
                        tag.endsWith("br") -> if (inParagraph) paraBuilder.append("\n")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph) {
                        paraBuilder.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when {
                        tag.endsWith("p") -> {
                            val line = sanitizeWinAnsi(paraBuilder.toString()).trim()
                            if (inTableCell) {
                                if (line.isNotEmpty()) currentRow.add(line)
                            } else if (line.isNotEmpty()) {
                                blocks.add(DocxBlock(text = line, isHeading = isHeading, isBold = isBold))
                            }
                            inParagraph = false
                        }
                        tag.endsWith("tc") -> inTableCell = false
                        tag.endsWith("tr") -> {
                            if (currentRow.isNotEmpty()) {
                                blocks.add(DocxBlock(text = "", tableRow = currentRow.toList()))
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        val destFile = uniquePdfFile(title)
        PDDocument().use { doc ->
            var page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            var cs = PDPageContentStream(doc, page)
            val margin = 48f
            val usableWidth = page.mediaBox.width - margin * 2
            var cursorY = page.mediaBox.height - margin

            fun ensureSpace(needed: Float) {
                if (cursorY - needed < margin) {
                    cs.close()
                    page = PDPage(PDRectangle.A4)
                    doc.addPage(page)
                    cs = PDPageContentStream(doc, page)
                    cursorY = page.mediaBox.height - margin
                }
            }

            for (block in blocks) {
                if (block.tableRow != null) {
                    val cols = max(1, block.tableRow.size)
                    val colW = usableWidth / cols
                    val rowH = 22f
                    ensureSpace(rowH + 4f)
                    block.tableRow.forEachIndexed { idx, cellText ->
                        val cellX = margin + idx * colW
                        cs.setStrokingColor(180, 185, 195)
                        cs.addRect(cellX, cursorY - rowH, colW, rowH)
                        cs.stroke()
                        val clipped = cellText.take(max(8, (colW / 5.5f).toInt()))
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, 9.5f)
                        cs.setNonStrokingColor(25, 28, 34)
                        cs.newLineAtOffset(cellX + 5f, cursorY - 15f)
                        cs.showText(clipped)
                        cs.endText()
                    }
                    cursorY -= (rowH + 2f)
                } else {
                    val font = when {
                        block.isHeading -> PDType1Font.HELVETICA_BOLD
                        block.isBold -> PDType1Font.HELVETICA_BOLD
                        else -> PDType1Font.HELVETICA
                    }
                    val fontSize = if (block.isHeading) 15f else 11f
                    val leading = if (block.isHeading) 21f else 16f
                    if (block.isHeading) cursorY -= 6f

                    val wrappedLines = wrapText(block.text, font, fontSize, usableWidth)
                    for (wl in wrappedLines) {
                        ensureSpace(leading)
                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setNonStrokingColor(20, 22, 28)
                        cs.newLineAtOffset(margin, cursorY)
                        cs.showText(wl)
                        cs.endText()
                        cursorY -= leading
                    }
                    cursorY -= 6f
                }
            }
            cs.close()
            doc.save(destFile)
        }
        renderPage1Thumbnail(destFile, forceRefresh = true)
        destFile
    }

    private fun convertXlsxBytesToPdf(bytes: ByteArray, title: String): File {
        val entries = readZipEntries(bytes)
        val sharedStrings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { ssBytes ->
            val p = Xml.newPullParser()
            p.setInput(ByteArrayInputStream(ssBytes), "UTF-8")
            var inSi = false
            val sb = StringBuilder()
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> if (p.name?.endsWith("si") == true) {
                        inSi = true
                        sb.clear()
                    }
                    XmlPullParser.TEXT -> if (inSi) sb.append(p.text)
                    XmlPullParser.END_TAG -> if (p.name?.endsWith("si") == true) {
                        sharedStrings.add(sanitizeWinAnsi(sb.toString()))
                        inSi = false
                    }
                }
                ev = p.next()
            }
        }

        val sheetKeys = entries.keys.filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }.sorted()
        val sheetsData = mutableListOf<Pair<String, List<List<String>>>>()

        sheetKeys.forEachIndexed { index, key ->
            val rows = mutableListOf<List<String>>()
            val p = Xml.newPullParser()
            p.setInput(ByteArrayInputStream(entries[key]!!), "UTF-8")
            var currentRow = mutableListOf<String>()
            var cellType = ""
            var inVal = false
            val valBuilder = StringBuilder()
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                val tag = p.name ?: ""
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        when {
                            tag.endsWith("row") -> currentRow = mutableListOf()
                            tag.endsWith("c") -> {
                                cellType = p.getAttributeValue(null, "t") ?: ""
                                valBuilder.clear()
                            }
                            tag.endsWith("v") || tag.endsWith("t") -> {
                                inVal = true
                                valBuilder.clear()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> if (inVal) valBuilder.append(p.text)
                    XmlPullParser.END_TAG -> {
                        when {
                            tag.endsWith("v") || tag.endsWith("t") -> inVal = false
                            tag.endsWith("c") -> {
                                val raw = valBuilder.toString().trim()
                                val resolved = if (cellType == "s") {
                                    val idx = raw.toIntOrNull() ?: -1
                                    if (idx in sharedStrings.indices) sharedStrings[idx] else raw
                                } else {
                                    sanitizeWinAnsi(raw)
                                }
                                currentRow.add(resolved)
                            }
                            tag.endsWith("row") -> {
                                if (currentRow.any { it.isNotBlank() }) rows.add(currentRow.toList())
                            }
                        }
                    }
                }
                ev = p.next()
            }
            sheetsData.add("Sheet ${index + 1}" to rows)
        }

        return renderGridSheetsToPdf(sheetsData, title)
    }

    private fun convertCsvBytesToPdf(bytes: ByteArray, title: String): File {
        val text = bytes.toString(Charsets.UTF_8)
        val rows = text.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                parseCsvLine(line).map { sanitizeWinAnsi(it) }
            }
        return renderGridSheetsToPdf(listOf(title to rows), title)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        result.add(cur.toString().trim())
        return result
    }

    private fun renderGridSheetsToPdf(
        sheets: List<Pair<String, List<List<String>>>>,
        title: String
    ): File {
        val destFile = uniquePdfFile(title)
        val landscapeA4 = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
        PDDocument().use { doc ->
            var page = PDPage(landscapeA4)
            doc.addPage(page)
            var cs = PDPageContentStream(doc, page)
            val margin = 36f
            val usableW = landscapeA4.width - margin * 2
            var cursorY = landscapeA4.height - margin

            fun newPage() {
                cs.close()
                page = PDPage(landscapeA4)
                doc.addPage(page)
                cs = PDPageContentStream(doc, page)
                cursorY = landscapeA4.height - margin
            }

            for ((sheetName, rows) in sheets) {
                if (cursorY - 48f < margin) newPage()
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 13f)
                cs.setNonStrokingColor(229, 37, 33)
                cs.newLineAtOffset(margin, cursorY)
                cs.showText(sanitizeWinAnsi(sheetName))
                cs.endText()
                cursorY -= 22f

                val maxCols = max(1, rows.maxOfOrNull { it.size } ?: 1).coerceAtMost(12)
                val colW = usableW / maxCols
                val rowH = 20f

                rows.forEachIndexed { rIdx, row ->
                    if (cursorY - rowH < margin) newPage()
                    if (rIdx == 0) {
                        cs.setNonStrokingColor(235, 238, 244)
                        cs.addRect(margin, cursorY - rowH, usableW, rowH)
                        cs.fill()
                    }
                    for (cIdx in 0 until maxCols) {
                        val cellX = margin + cIdx * colW
                        cs.setStrokingColor(195, 200, 210)
                        cs.addRect(cellX, cursorY - rowH, colW, rowH)
                        cs.stroke()
                        val cellVal = row.getOrElse(cIdx) { "" }
                            .take(max(6, (colW / 5.4f).toInt()))
                        cs.beginText()
                        cs.setFont(
                            if (rIdx == 0) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA,
                            9f
                        )
                        cs.setNonStrokingColor(25, 28, 34)
                        cs.newLineAtOffset(cellX + 4f, cursorY - 14f)
                        cs.showText(cellVal)
                        cs.endText()
                    }
                    cursorY -= rowH
                }
                cursorY -= 16f
            }
            cs.close()
            doc.save(destFile)
        }
        renderPage1Thumbnail(destFile, forceRefresh = true)
        return destFile
    }

    private fun wrapText(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            val words = paragraph.split(" ")
            val current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                val w = try {
                    font.getStringWidth(candidate) / 1000f * fontSize
                } catch (_: Exception) {
                    candidate.length * fontSize * 0.52f
                }
                if (w <= maxWidth) {
                    current.clear()
                    current.append(candidate)
                } else {
                    if (current.isNotEmpty()) result.add(current.toString())
                    current.clear()
                    current.append(word)
                }
            }
            if (current.isNotEmpty()) result.add(current.toString())
        }
        return if (result.isEmpty()) listOf("") else result
    }

    suspend fun mergePdfs(filePaths: List<String>, outputTitle: String): File = withContext(Dispatchers.IO) {
        val destFile = uniquePdfFile(outputTitle)
        val merger = PDFMergerUtility()
        merger.destinationFileName = destFile.absolutePath
        for (path in filePaths) {
            val f = File(path)
            if (f.exists()) merger.addSource(f)
        }
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        renderPage1Thumbnail(destFile, forceRefresh = true)
        destFile
    }

    fun renamePdf(filePath: String, newName: String): File {
        val src = File(filePath)
        val target = uniquePdfFile(newName)
        if (src.renameTo(target)) {
            val starred = getStarredSet()
            if (starred.remove(filePath)) {
                starred.add(target.absolutePath)
                prefs.edit().putStringSet("starred_paths", starred).apply()
            }
            renderPage1Thumbnail(target, forceRefresh = true)
            return target
        }
        return src
    }

    fun duplicatePdf(filePath: String, newName: String): File {
        val src = File(filePath)
        val target = uniquePdfFile(newName)
        src.copyTo(target, overwrite = true)
        renderPage1Thumbnail(target, forceRefresh = true)
        return target
    }

    fun deletePdf(filePath: String) {
        val f = File(filePath)
        if (f.exists()) f.delete()
        val starred = getStarredSet()
        if (starred.remove(filePath)) {
            prefs.edit().putStringSet("starred_paths", starred).apply()
        }
    }

    fun getBookmarks(filePath: String): List<BookmarkItem> {
        val raw = prefs.getString("bookmarks_${filePath.hashCode()}", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BookmarkItem(
                    id = obj.getString("id"),
                    pageIndex = obj.getInt("pageIndex"),
                    title = obj.getString("title"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addBookmark(filePath: String, pageIndex: Int, title: String): List<BookmarkItem> {
        val list = getBookmarks(filePath).toMutableList()
        list.add(BookmarkItem(pageIndex = pageIndex, title = title.ifBlank { "Page ${pageIndex + 1}" }))
        saveBookmarks(filePath, list)
        return list
    }

    fun deleteBookmark(filePath: String, bookmarkId: String): List<BookmarkItem> {
        val list = getBookmarks(filePath).filterNot { it.id == bookmarkId }
        saveBookmarks(filePath, list)
        return list
    }

    private fun saveBookmarks(filePath: String, list: List<BookmarkItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("pageIndex", item.pageIndex)
                    put("title", item.title)
                    put("createdAt", item.createdAt)
                }
            )
        }
        prefs.edit().putString("bookmarks_${filePath.hashCode()}", arr.toString()).apply()
    }
}
