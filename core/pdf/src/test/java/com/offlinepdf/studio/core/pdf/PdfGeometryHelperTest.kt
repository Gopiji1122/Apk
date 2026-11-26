package com.offlinepdf.studio.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfGeometryHelperTest {

    @Test
    fun normalizeBox_mapsPdfPointsToUnitSquare() {
        val box = PdfGeometryHelper.normalizeBox(
            minX = 50f, minY = 100f, maxX = 150f, maxY = 120f,
            pageLeft = 0f, pageTop = 0f, pageWidth = 500f, pageHeight = 1000f
        )
        assertEquals(0.1f, box.leftNorm, 0.001f)
        assertEquals(0.1f, box.topNorm, 0.001f)
        assertEquals(0.3f, box.rightNorm, 0.001f)
        assertEquals(0.12f, box.bottomNorm, 0.001f)
    }

    @Test
    fun rotateNormalizedBox_rotates90And180Accurately() {
        val box = PdfSearchBox(0.1f, 0.2f, 0.3f, 0.4f)
        val rot90 = PdfGeometryHelper.rotateNormalizedBox(box, 90)
        assertEquals(0.6f, rot90.leftNorm, 0.001f)
        assertEquals(0.1f, rot90.topNorm, 0.001f)
        assertEquals(0.8f, rot90.rightNorm, 0.001f)
        assertEquals(0.3f, rot90.bottomNorm, 0.001f)

        val rot360 = PdfGeometryHelper.rotateNormalizedBox(box, 360)
        assertEquals(box, rot360)
    }

    @Test
    fun hitNavigation_cyclesCleanly() {
        assertEquals(1, PdfGeometryHelper.nextHitIndex(0, 3))
        assertEquals(0, PdfGeometryHelper.nextHitIndex(2, 3))
        assertEquals(2, PdfGeometryHelper.previousHitIndex(0, 3))
        assertEquals(-1, PdfGeometryHelper.nextHitIndex(0, 0))
    }
}
