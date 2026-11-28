package com.offlinepdf.studio.core.imageprocessing

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Minimal streaming PDF writer that embeds JPEG data directly (DCTDecode).
 *
 * Android's PdfDocument re-encodes bitmaps losslessly (Flate), which makes scans 5–10× larger than
 * necessary. Embedding the JPEG as-is keeps files small and the quality exactly what the user chose.
 * Pages are written one at a time, so memory use stays flat regardless of page count.
 */
internal class JpegPdfWriter(rawOut: OutputStream, private val title: String) {

    private class Counting(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private val out = Counting(rawOut)
    private val offsets = ArrayList<Long>()   // index = object number - 1
    private var pageCount = 0
    private val pageObjectIds = ArrayList<Int>()

    init {
        writeAscii("%PDF-1.4\n")
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
        // Reserve object 1 (catalog) and 2 (pages); written in finish().
        offsets.add(-1L); offsets.add(-1L)
    }

    private fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

    private fun beginObject(): Int {
        offsets.add(out.count)
        val id = offsets.size
        writeAscii("$id 0 obj\n")
        return id
    }

    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)

    /** Draws the JPEG inside the rectangle (x, y, w, h) measured from the bottom-left of the page. */
    fun addPage(
        jpeg: ByteArray, imgW: Int, imgH: Int,
        pageW: Float, pageH: Float, x: Float, y: Float, w: Float, h: Float
    ) {
        val content = "q ${f(w)} 0 0 ${f(h)} ${f(x)} ${f(y)} cm /Im0 Do Q"
        val pageId = offsets.size + 1
        val contentId = pageId + 1
        val imageId = pageId + 2

        beginObject()
        writeAscii(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(pageW)} ${f(pageH)}] " +
                "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
        )
        beginObject()
        writeAscii("<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")
        beginObject()
        writeAscii(
            "<< /Type /XObject /Subtype /Image /Width $imgW /Height $imgH /ColorSpace /DeviceRGB " +
                "/BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n"
        )
        out.write(jpeg)
        writeAscii("\nendstream\nendobj\n")
        pageObjectIds.add(pageId)
        pageCount++
    }

    fun finish() {
        offsets[0] = out.count
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        offsets[1] = out.count
        val kids = pageObjectIds.joinToString(" ") { "$it 0 R" }
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $pageCount >>\nendobj\n")

        val hex = StringBuilder("FEFF")
        title.forEach { hex.append(String.format(Locale.US, "%04X", it.code)) }
        val infoId = beginObject()
        writeAscii("<< /Title <$hex> /Producer (Offline PDF Studio) >>\nendobj\n")

        val xrefPos = out.count
        val total = offsets.size + 1
        writeAscii("xref\n0 $total\n0000000000 65535 f \n")
        offsets.forEach { writeAscii(String.format(Locale.US, "%010d 00000 n \n", it)) }
        writeAscii("trailer\n<< /Size $total /Root 1 0 R /Info $infoId 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        out.flush()
    }
}
