package com.offlinepdf.studio.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfModelsTest {
    @Test
    fun pdfMetadata_defaultsAreSafe() {
        val meta = PdfMetadata()
        assertEquals("PDF", meta.format)
        assertEquals("None", meta.encryption)
        assertEquals(0, meta.pageCount)
    }

    @Test
    fun pdfPageSize_preservesPointDimensions() {
        val a4 = PdfPageSize(widthPt = 595f, heightPt = 842f)
        assertEquals(595f, a4.widthPt, 0.01f)
        assertEquals(842f, a4.heightPt, 0.01f)
    }
}
