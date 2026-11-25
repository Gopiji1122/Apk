package com.aurapdf.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object OfficeConverter {

    private fun sanitize(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch.code in 32..126 -> sb.append(ch)
                ch == '\t' -> sb.append("    ")
                ch == '\u2018' || ch == '\u2019' -> sb.append('\'')
                ch == '\u201C' || ch == '\u201D' -> sb.append("\"")
                ch == '\u2013' || ch == '\u2014' -> sb.append('-')
                ch == '\u2022' -> sb.append('*')
                else -> sb.append(' ')
            }
        }
        return sb.toString()
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        val clean = sanitize(text)
        if (clean.isBlank()) return listOf("")
        val words = clean.split(Regex("\\s+"))
        var line = StringBuilder()
        for (w in words) {
            if (line.length + w.length + 1 > maxChars) {
                if (line.isNotEmpty()) result.add(line.toString())
                line = StringBuilder(w.take(maxChars))
            } else {
                if (line.isNotEmpty()) line.append(' ')
                line.append(w)
            }
        }
        if (line.isNotEmpty()) result.add(line.toString())
        return result
    }

    fun convertWordDocxToPdf(context: Context, uri: Uri, outputFile: File, title: String): Int {
        val paragraphs = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                        val parser = Xml.newPullParser()
                        parser.setInput(zis, "UTF-8")
                        var event = parser.eventType
                        var currentPara = StringBuilder()
                        while (event != XmlPullParser.END_DOCUMENT) {
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    if (parser.name == "p") currentPara = StringBuilder()
                                    else if (parser.name == "t") currentPara.append(parser.nextText())
                                    else if (parser.name == "tab") currentPara.append("    ")
                                }
                                XmlPullParser.END_TAG -> {
                                    if (parser.name == "p") paragraphs.add(currentPara.toString())
                                }
                            }
                            event = parser.next()
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("Unable to open Word file.")

        if (paragraphs.isEmpty()) paragraphs.add("(No readable text found in Word document.)")

        val doc = PDDocument()
        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        var cs = PDPageContentStream(doc, page)
        val margin = 48f
        var y = page.mediaBox.height - margin

        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 15f)
        cs.newLineAtOffset(margin, y)
        cs.showText(sanitize(title).take(70))
        cs.endText()
        y -= 28f

        for (para in paragraphs) {
            for (line in wrapText(para, 84)) {
                if (y < margin + 20f) {
                    cs.close()
                    page = PDPage(PDRectangle.A4)
                    doc.addPage(page)
                    cs = PDPageContentStream(doc, page)
                    y = page.mediaBox.height - margin
                }
                if (line.isNotEmpty()) {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 11f)
                    cs.newLineAtOffset(margin, y)
                    cs.showText(line)
                    cs.endText()
                }
                y -= 16f
            }
            y -= 4f
        }
        cs.close()
        doc.save(outputFile)
        val count = doc.numberOfPages
        doc.close()
        return count
    }

    fun convertExcelOrCsvToPdf(context: Context, uri: Uri, fileName: String, outputFile: File): Int {
        val rows = if (fileName.endsWith(".csv", ignoreCase = true)) readCsvRows(context, uri) else readXlsxRows(context, uri)
        if (rows.isEmpty()) throw IllegalStateException("Spreadsheet is empty or unsupported format.")

        val doc = PDDocument()
        val rect = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
        var page = PDPage(rect)
        doc.addPage(page)
        var cs = PDPageContentStream(doc, page)

        val margin = 36f
        val tableWidth = rect.width - margin * 2
        val maxCols = rows.maxOf { it.size }.coerceIn(1, 10)
        val colWidth = tableWidth / maxCols
        val rowHeight = 20f
        var y = rect.height - margin

        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 13f)
        cs.newLineAtOffset(margin, y)
        cs.showText(sanitize(fileName).take(80))
        cs.endText()
        y -= 24f

        rows.forEachIndexed { rIdx, row ->
            if (y < margin + rowHeight) {
                cs.close()
                page = PDPage(rect)
                doc.addPage(page)
                cs = PDPageContentStream(doc, page)
                y = rect.height - margin
            }
            if (rIdx == 0) {
                cs.setNonStrokingColor(230, 230, 230)
                cs.addRect(margin, y - rowHeight + 4f, tableWidth, rowHeight)
                cs.fill()
            } else if (rIdx % 2 == 0) {
                cs.setNonStrokingColor(246, 246, 246)
                cs.addRect(margin, y - rowHeight + 4f, tableWidth, rowHeight)
                cs.fill()
            }
            cs.setNonStrokingColor(0, 0, 0)
            cs.setStrokingColor(190, 190, 190)
            cs.setLineWidth(0.5f)
            cs.addRect(margin, y - rowHeight + 4f, tableWidth, rowHeight)
            cs.stroke()

            val maxChars = (colWidth / 5.4f).toInt().coerceAtLeast(6)
            for (c in 0 until maxCols) {
                val cell = sanitize(row.getOrNull(c) ?: "").take(maxChars)
                cs.beginText()
                cs.setFont(if (rIdx == 0) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, 9f)
                cs.newLineAtOffset(margin + c * colWidth + 4f, y - 10f)
                cs.showText(cell)
                cs.endText()
            }
            y -= rowHeight
        }
        cs.close()
        doc.save(outputFile)
        val pages = doc.numberOfPages
        doc.close()
        return pages
    }

    private fun readCsvRows(context: Context, uri: Uri): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { seq ->
                seq.forEach { line ->
                    if (line.isNotBlank()) rows.add(line.split(",").map { it.trim().removeSurrounding("\"") })
                }
            }
        }
        return rows
    }

    private fun readXlsxRows(context: Context, uri: Uri): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        var sheetXmlBytes: ByteArray? = null
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                            val bytes = zis.readBytes()
                            val parser = Xml.newPullParser()
                            parser.setInput(bytes.inputStream(), "UTF-8")
                            var ev = parser.eventType
                            var sb = StringBuilder()
                            var inSi = false
                            while (ev != XmlPullParser.END_DOCUMENT) {
                                when (ev) {
                                    XmlPullParser.START_TAG -> {
                                        if (parser.name == "si") { sb = StringBuilder(); inSi = true }
                                        else if (inSi && parser.name == "t") sb.append(parser.nextText())
                                    }
                                    XmlPullParser.END_TAG -> {
                                        if (parser.name == "si") { sharedStrings.add(sb.toString()); inSi = false }
                                    }
                                }
                                ev = parser.next()
                            }
                        }
                        entry.name.equals("xl/worksheets/sheet1.xml", ignoreCase = true) -> {
                            sheetXmlBytes = zis.readBytes()
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }
        val sheetBytes = sheetXmlBytes ?: return emptyList()
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(sheetBytes.inputStream(), "UTF-8")
        var ev = parser.eventType
        var currentRow = mutableListOf<String>()
        var cellType: String? = null
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> cellType = parser.getAttributeValue(null, "t")
                    "v" -> {
                        val raw = parser.nextText()
                        currentRow.add(if (cellType == "s") raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw else raw)
                    }
                    "t" -> if (cellType == "inlineStr") currentRow.add(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "row" && currentRow.isNotEmpty()) rows.add(currentRow)
            }
            ev = parser.next()
        }
        return rows
    }
}
