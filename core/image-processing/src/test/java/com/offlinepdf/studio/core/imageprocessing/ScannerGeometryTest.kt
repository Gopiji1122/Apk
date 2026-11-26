package com.offlinepdf.studio.core.imageprocessing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerGeometryTest {

    @Test
    fun fromUnordered_ordersCornersCanonically() {
        val scrambled = listOf(
            NormalizedPoint(0.9f, 0.85f), // bottomRight
            NormalizedPoint(0.1f, 0.12f), // topLeft
            NormalizedPoint(0.12f, 0.88f), // bottomLeft
            NormalizedPoint(0.88f, 0.1f)  // topRight
        )
        val quad = DocumentQuad.fromUnordered(scrambled)
        assertEquals(0.1f, quad.topLeft.x, 0.001f)
        assertEquals(0.88f, quad.topRight.x, 0.001f)
        assertEquals(0.9f, quad.bottomRight.x, 0.001f)
        assertEquals(0.12f, quad.bottomLeft.x, 0.001f)
    }

    @Test
    fun stabilityTracker_triggersOnlyAfterSteadyFramesAndRespectsCooldown() {
        val tracker = QuadStabilityTracker(requiredSteadyFrames = 3, maxMovementDelta = 0.02f, cooldownMillis = 2000L)
        val q = DocumentQuad.fullFrame(0.1f)

        assertFalse(tracker.onFrame(q, nowMillis = 1000L).shouldAutoCapture)
        assertFalse(tracker.onFrame(q, nowMillis = 1100L).shouldAutoCapture)
        assertTrue(tracker.onFrame(q, nowMillis = 3000L).shouldAutoCapture)

        // Immediately after trigger, cooldown prevents duplicate capture
        assertFalse(tracker.onFrame(q, nowMillis = 3100L).shouldAutoCapture)
        assertFalse(tracker.onFrame(q, nowMillis = 3200L).shouldAutoCapture)
        assertFalse(tracker.onFrame(q, nowMillis = 3300L).shouldAutoCapture)
    }

    @Test
    fun exportLayoutMath_computesA4PortraitAndAutoLandscape() {
        val portrait = ExportLayoutMath.resolvePageDimensionsPt(
            ExportPageSize.A4, ExportOrientation.AUTO, 1200, 1600
        )
        assertEquals(595 to 842, portrait)

        val landscape = ExportLayoutMath.resolvePageDimensionsPt(
            ExportPageSize.A4, ExportOrientation.AUTO, 1600, 1200
        )
        assertEquals(842 to 595, landscape)
    }

    @Test
    fun rotated90Clockwise_andLerp_keepValidUnitBounds() {
        val q1 = DocumentQuad.fullFrame(0.1f)
        val q2 = q1.rotated90Clockwise()
        q2.asList().forEach { p ->
            assertTrue(p.x in 0f..1f)
            assertTrue(p.y in 0f..1f)
        }
        val mid = q1.lerp(DocumentQuad.fullFrame(0.2f), 0.5f)
        assertEquals(0.15f, mid.topLeft.x, 0.001f)
    }
}
