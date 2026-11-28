package com.offlinepdf.studio.core.imageprocessing

import com.offlinepdf.studio.core.imageprocessing.pipeline.AdaptiveToneMath
import com.offlinepdf.studio.core.imageprocessing.pipeline.FrameQualityReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePipelineTest {

    @Test
    fun whiteBalanceGains_neutralizeYellowCastSafelyWithinBounds() {
        // Warm yellow tungsten cast: R=210, G=195, B=150
        val (gR, gG, gB) = AdaptiveToneMath.computeSafeWhiteBalanceGains(210.0, 195.0, 150.0)
        assertTrue(gR < 1.0f)
        assertTrue(gB > 1.0f)
        assertTrue(gR in 0.84f..1.22f)
        assertTrue(gG in 0.84f..1.22f)
        assertTrue(gB in 0.84f..1.22f)
    }

    @Test
    fun dynamicWhitePoint_clampsSafelyAndProtectsChroma() {
        assertEquals(225f, AdaptiveToneMath.computeDynamicWhitePoint(236f, 11f), 0.01f)
        assertEquals(216f, AdaptiveToneMath.computeDynamicWhitePoint(190f, 11f), 0.01f)
        assertEquals(240f, AdaptiveToneMath.computeDynamicWhitePoint(255f, 11f), 0.01f)

        // Neutral gray paper (chroma=2) gets 0 protection -> snaps to white
        assertEquals(0f, AdaptiveToneMath.evaluateChromaProtectionWeight(2.0f), 0.001f)
        // Colored red/blue ink (chroma=18) gets full protection -> never washed out
        assertEquals(1f, AdaptiveToneMath.evaluateChromaProtectionWeight(18.0f), 0.001f)
    }

    @Test
    fun toneTables_snapPaperToWhiteAndKeepColorInkUnclipped() {
        val neutral = AdaptiveToneMath.buildNeutralToneTable(whitePoint = 228f)
        assertEquals(0, neutral[10].toInt() and 0xFF)
        assertEquals(255, neutral[230].toInt() and 0xFF)

        val color = AdaptiveToneMath.buildColorInkToneTable()
        // Colored ink never clips to 255 even at high L
        assertTrue((color[240].toInt() and 0xFF) < 245)
    }

    @Test
    fun frameQualityReport_penalizesExcessiveGlare() {
        val clean = FrameQualityReport(120.0, 150.0, 0.01f, true, true, false)
        val glare = FrameQualityReport(120.0, 150.0, 0.25f, true, true, true)
        assertTrue(clean.compositeScore > glare.compositeScore)
    }
}
