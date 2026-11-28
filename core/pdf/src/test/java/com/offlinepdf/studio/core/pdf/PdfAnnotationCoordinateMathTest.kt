package com.offlinepdf.studio.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationCoordinateMathTest {

    @Test
    fun argbToRgbFloats_extractsNormalizedChannels() {
        val rgb = PdfAnnotationCoordinateMath.argbToRgbFloats(0xFFFF8000.toInt())
        assertEquals(1.0f, rgb[0], 0.01f)
        assertEquals(0.502f, rgb[1], 0.01f)
        assertEquals(0.0f, rgb[2], 0.01f)
    }

    @Test
    fun toPdfCoordinates_mapsUnitSquareToA4Bounds() {
        assertEquals(297.5f, PdfAnnotationCoordinateMath.toPdfX(0.5f, 0f, 595f), 0.01f)
        assertEquals(421.0f, PdfAnnotationCoordinateMath.toPdfY(0.5f, 0f, 842f), 0.01f)
    }

    @Test
    fun hitTest_detectsTapOnStickyNoteAndMissesFarTap() {
        val note = PdfEditAnnotation.StickyNote(
            pageIndex = 0,
            colorArgb = 0xFFFFEB3B.toInt(),
            xNorm = 0.3f,
            yNorm = 0.4f,
            text = "Check this"
        )
        assertTrue(PdfAnnotationCoordinateMath.hitTest(note, 0.31f, 0.41f))
        assertFalse(PdfAnnotationCoordinateMath.hitTest(note, 0.8f, 0.8f))
    }
}
