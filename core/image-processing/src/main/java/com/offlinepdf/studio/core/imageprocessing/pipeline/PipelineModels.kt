package com.offlinepdf.studio.core.imageprocessing.pipeline

import kotlin.math.roundToInt

/**
 * Quantitative quality validation output from Stage 14 of the 14-stage pipeline.
 */
data class DocumentQualityReport(
    val backgroundUniformityScore: Float, // 0..100 (higher = more uniform paper)
    val inkContrastScore: Float,          // 0..100 (higher = stronger text-to-paper separation)
    val edgeSharpnessScore: Float,        // Laplacian variance
    val glareRatio: Float,                // 0..1 fraction of clipped highlight pixels
    val blurWarning: Boolean,
    val dynamicWhitePoint: Float,
    val whiteBalanceGains: Triple<Float, Float, Float>,
    val processingTimeMs: Long
)

data class FrameQualityReport(
    val sharpnessVariance: Double,
    val meanLuminance: Double,
    val glareRatio: Float,
    val isSharp: Boolean,
    val isWellExposed: Boolean,
    val hasExcessiveGlare: Boolean
) {
    val compositeScore: Double
        get() {
            val sharpTerm = sharpnessVariance.coerceIn(0.0, 250.0) / 250.0
            val expPenalty = kotlin.math.abs(meanLuminance - 145.0) / 145.0
            val glarePenalty = glareRatio * 2.5
            return (sharpTerm * 0.65 + (1.0 - expPenalty).coerceIn(0.0, 1.0) * 0.35 - glarePenalty)
                .coerceIn(0.0, 1.0)
        }
}

/**
 * Pure Kotlin math helpers for unit-testable adaptive white-balance and tone-curve bounds.
 */
object AdaptiveToneMath {
    fun computeSafeWhiteBalanceGains(medR: Double, medG: Double, medB: Double): Triple<Float, Float, Float> {
        val target = (medR + medG + medB) / 3.0
        val gR = (target / medR.coerceAtLeast(1.0)).toFloat().coerceIn(0.84f, 1.22f)
        val gG = (target / medG.coerceAtLeast(1.0)).toFloat().coerceIn(0.84f, 1.22f)
        val gB = (target / medB.coerceAtLeast(1.0)).toFloat().coerceIn(0.84f, 1.22f)
        return Triple(gR, gG, gB)
    }

    fun computeDynamicWhitePoint(neutralPaperP60: Float, offset: Float = 11f): Float {
        return (neutralPaperP60 - offset).coerceIn(216f, 240f)
    }

    fun evaluateChromaProtectionWeight(labChroma: Float): Float {
        // Neutral paper has chroma < 4.0; colored red/blue/green ink has chroma >= 5..40
        return ((labChroma - 4.0f) / 7.5f).coerceIn(0f, 1f)
    }

    fun buildNeutralToneTable(whitePoint: Float, blackPoint: Float = 18f, gamma: Double = 1.48): ByteArray {
        val wp = whitePoint.coerceIn(200f, 246f).toDouble()
        val bp = blackPoint.coerceIn(0f, 45f).toDouble()
        val denom = (wp - bp).coerceAtLeast(1.0)
        return ByteArray(256) { i ->
            val v = when {
                i >= wp -> 255.0
                i <= bp -> 0.0
                else -> Math.pow(((i - bp) / denom).coerceIn(0.0, 1.0), gamma) * 255.0
            }
            v.roundToInt().coerceIn(0, 255).toByte()
        }
    }

    fun buildColorInkToneTable(blackPoint: Float = 14f, gamma: Double = 1.36): ByteArray {
        val bp = blackPoint.toDouble()
        val denom = (255.0 - bp).coerceAtLeast(1.0)
        return ByteArray(256) { i ->
            val v = if (i <= bp) 0.0 else Math.pow(((i - bp) / denom).coerceIn(0.0, 1.0), gamma) * 240.0
            v.roundToInt().coerceIn(0, 242).toByte()
        }
    }
}
