package com.offlinepdf.studio.core.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class FrameDetectionResult(
    val quad: DocumentQuad?,
    val confidence: Float = 0f,
    val sharpnessScore: Double = 100.0,
    val meanLuminance: Double = 128.0,
    val usedNeuralModel: Boolean = false
) {
    val isSharpEnough: Boolean get() = sharpnessScore >= 5.0
    val isWellExposed: Boolean get() = meanLuminance in 18.0..250.0
}

class QuadStabilityTracker(
    private val requiredSteadyFrames: Int = 4,
    private val maxMovementDelta: Float = 0.055f,
    private val cooldownMillis: Long = 2200L,
    private val maxMissedFrames: Int = 3
) {
    private var smoothedQuad: DocumentQuad? = null
    private var lastValidConfidence: Float = 0f
    private var steadyCount: Int = 0
    private var missedCount: Int = 0
    private var lastTriggerMillis: Long = 0L

    data class StabilityState(
        val detectedQuad: DocumentQuad?,
        val confidence: Float,
        val progress: Float,
        val shouldAutoCapture: Boolean,
        val qualityHint: String? = null
    )

    fun onFrame(rawQuad: DocumentQuad?, nowMillis: Long = System.currentTimeMillis()): StabilityState {
        return onFrameEvaluated(
            result = FrameDetectionResult(
                quad = rawQuad,
                confidence = if (rawQuad != null) 0.85f else 0f
            ),
            nowMillis = nowMillis
        )
    }

    fun onFrameEvaluated(
        result: FrameDetectionResult,
        sensitivity: String = "NORMAL",
        nowMillis: Long = System.currentTimeMillis()
    ): StabilityState {
        val rawQuad = result.quad
        val minConf = when (sensitivity) {
            "HIGH" -> 0.35f
            "LOW" -> 0.62f
            else -> 0.45f
        }
        val effectiveDelta = when (sensitivity) {
            "HIGH" -> 0.075f
            "LOW" -> 0.040f
            else -> maxMovementDelta
        }

        if (rawQuad == null || result.confidence < minConf) {
            missedCount++
            if (missedCount > maxMissedFrames) {
                smoothedQuad = null
                lastValidConfidence = 0f
                steadyCount = 0
                return StabilityState(null, 0f, 0f, false, null)
            }
            val progress = (steadyCount.toFloat() / requiredSteadyFrames.toFloat()).coerceIn(0f, 1f)
            return StabilityState(smoothedQuad, lastValidConfidence, progress, false, null)
        }

        missedCount = 0
        lastValidConfidence = result.confidence
        val prev = smoothedQuad
        val nextSmoothed = if (prev == null) rawQuad else prev.lerp(rawQuad, 0.45f)
        smoothedQuad = nextSmoothed

        if (!result.isSharpEnough) {
            steadyCount = (steadyCount - 1).coerceAtLeast(0)
            return StabilityState(nextSmoothed, lastValidConfidence, steadyCount.toFloat() / requiredSteadyFrames, false, "Focusing — hold still")
        }
        if (!result.isWellExposed) {
            steadyCount = (steadyCount - 1).coerceAtLeast(0)
            val hint = if (result.meanLuminance < 18.0) "Low light — try flash" else "Too bright — tilt slightly"
            return StabilityState(nextSmoothed, lastValidConfidence, steadyCount.toFloat() / requiredSteadyFrames, false, hint)
        }

        if (prev != null && rawQuad.maxCornerDelta(prev) <= effectiveDelta) {
            steadyCount++
        } else {
            steadyCount = 1
        }

        val progress = (steadyCount.toFloat() / requiredSteadyFrames.toFloat()).coerceIn(0f, 1f)
        val cooldownReady = (nowMillis - lastTriggerMillis) >= cooldownMillis
        val trigger = steadyCount >= requiredSteadyFrames && cooldownReady
        if (trigger) {
            lastTriggerMillis = nowMillis
            steadyCount = 0
        }
        return StabilityState(nextSmoothed, lastValidConfidence, progress, trigger, null)
    }

    fun reset() {
        smoothedQuad = null
        lastValidConfidence = 0f
        steadyCount = 0
        missedCount = 0
    }
}

@Singleton
class DocumentEdgeDetector @Inject constructor(
    private val neuralDetector: NeuralDocAlignerDetector
) {
    private var reusableYBytes = ByteArray(0)

    val isNeuralActive: Boolean
        get() = neuralDetector.isModelReady

    /**
     * Primary live-camera analyzer accepting an upright RGB Bitmap (typically 320..480px)
     * so both Neural DocAligner (RGB 256x256) and Color-Aware HSV Paper Mask run cleanly.
     */
    fun analyzeCameraBitmap(bitmap: Bitmap): FrameDetectionResult {
        val rgba = Mat()
        val rgb = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)

            val sharpness = computeLaplacianVariance(gray)
            val meanLuma = Core.mean(gray).`val`[0]

            // Stage 0: Neural DocAligner (LCNet100 ONNX)
            val neuralHit = neuralDetector.detectFromRgbMat(rgb)
            if (neuralHit != null) {
                return FrameDetectionResult(
                    quad = neuralHit.first,
                    confidence = neuralHit.second,
                    sharpnessScore = sharpness,
                    meanLuminance = meanLuma,
                    usedNeuralModel = true
                )
            }

            // Stage 1: Color-Aware White-Paper Mask (ignores colored patterned bedsheets/desks)
            val hsvHit = detectPaperByColorSaturation(rgb)
            if (hsvHit != null) {
                return FrameDetectionResult(
                    quad = hsvHit.first,
                    confidence = hsvHit.second,
                    sharpnessScore = sharpness,
                    meanLuminance = meanLuma,
                    usedNeuralModel = false
                )
            }

            // Stage 2: Classic multi-stage grayscale contour detector with border-hugging rejection
            val (quad, conf) = detectBestQuadFromGray(gray)
            FrameDetectionResult(
                quad = quad,
                confidence = conf,
                sharpnessScore = sharpness,
                meanLuminance = meanLuma,
                usedNeuralModel = false
            )
        } catch (_: Throwable) {
            FrameDetectionResult(null, 0f)
        } finally {
            gray.release()
            rgb.release()
            rgba.release()
        }
    }

    /**
     * Legacy zero-Bitmap Y-plane entrypoint kept for compatibility.
     */
    fun analyzeYPlane(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        rotationDegrees: Int
    ): FrameDetectionResult {
        if (width <= 0 || height <= 0) return FrameDetectionResult(null, 0f)
        val needed = width * height
        if (reusableYBytes.size != needed) reusableYBytes = ByteArray(needed)

        val dup = yBuffer.duplicate()
        dup.rewind()
        if (rowStride == width) {
            dup.get(reusableYBytes, 0, needed)
        } else {
            var dstOffset = 0
            for (row in 0 until height) {
                dup.position(row * rowStride)
                dup.get(reusableYBytes, dstOffset, width)
                dstOffset += width
            }
        }

        val rawGray = Mat(height, width, CvType.CV_8UC1)
        val uprightGray = Mat()
        return try {
            rawGray.put(0, 0, reusableYBytes)
            when ((rotationDegrees % 360 + 360) % 360) {
                90 -> Core.rotate(rawGray, uprightGray, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(rawGray, uprightGray, Core.ROTATE_180)
                270 -> Core.rotate(rawGray, uprightGray, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> rawGray.copyTo(uprightGray)
            }
            val sharpness = computeLaplacianVariance(uprightGray)
            val meanLuma = Core.mean(uprightGray).`val`[0]
            val (quad, conf) = detectBestQuadFromGray(uprightGray)
            FrameDetectionResult(quad, conf, sharpness, meanLuma, false)
        } catch (_: Throwable) {
            FrameDetectionResult(null, 0f)
        } finally {
            uprightGray.release()
            rawGray.release()
        }
    }

    /**
     * Full-photo still detection (used right after capture and when tapping Auto in Crop).
     */
    fun detectDocumentQuad(bitmap: Bitmap): DocumentQuad? {
        val neuralHit = neuralDetector.detectFromBitmap(bitmap)
        if (neuralHit != null) return neuralHit.first

        val rgba = Mat()
        val rgb = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            val hsvHit = detectPaperByColorSaturation(rgb)
            if (hsvHit != null) return hsvHit.first

            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            detectBestQuadFromGray(gray).first
        } catch (_: Throwable) {
            null
        } finally {
            gray.release()
            rgb.release()
            rgba.release()
        }
    }

    /**
     * Isolates bright, low-saturation paper sheets (white/cream/off-white) against colorful
     * or patterned backgrounds (such as blue/floral bedsheets or wooden tables).
     */
    private fun detectPaperByColorSaturation(rgb: Mat): Pair<DocumentQuad, Float>? {
        val targetMax = 380.0
        val scale = min(1.0, targetMax / max(rgb.width(), rgb.height()).toDouble())
        val small = Mat()
        val hsv = Mat()
        val mask = Mat()
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        return try {
            if (scale < 1.0) {
                Imgproc.resize(rgb, small, Size(rgb.width() * scale, rgb.height() * scale))
            } else {
                rgb.copyTo(small)
            }
            Imgproc.cvtColor(small, hsv, Imgproc.COLOR_RGB2HSV)
            // White/off-white paper: any hue, low saturation (0..62), moderate-to-high value (125..255)
            Core.inRange(hsv, Scalar(0.0, 0.0, 125.0), Scalar(180.0, 62.0, 255.0), mask)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, openKernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, closeKernel)
            findBestQuadInBinary(mask, small.width(), small.height(), stageBonus = 0.06f)
        } finally {
            openKernel.release()
            closeKernel.release()
            mask.release()
            hsv.release()
            small.release()
        }
    }

    private fun computeLaplacianVariance(gray: Mat): Double {
        val sample = Mat()
        val lap = Mat()
        val mean = MatOfDouble()
        val std = MatOfDouble()
        return try {
            val scale = min(1.0, 320.0 / max(gray.width(), gray.height()).toDouble())
            if (scale < 1.0) {
                Imgproc.resize(gray, sample, Size(gray.width() * scale, gray.height() * scale))
            } else {
                gray.copyTo(sample)
            }
            Imgproc.Laplacian(sample, lap, CvType.CV_64F)
            Core.meanStdDev(lap, mean, std)
            val s = std.toArray().firstOrNull() ?: 0.0
            s * s
        } finally {
            std.release()
            mean.release()
            lap.release()
            sample.release()
        }
    }

    private fun detectBestQuadFromGray(gray: Mat): Pair<DocumentQuad?, Float> {
        val targetMax = 400.0
        val scale = min(1.0, targetMax / max(gray.width(), gray.height()).toDouble())
        val small = Mat()
        val blurred = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        return try {
            if (scale < 1.0) {
                Imgproc.resize(gray, small, Size(gray.width() * scale, gray.height() * scale))
            } else {
                gray.copyTo(small)
            }
            Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)

            var bestQuad: DocumentQuad? = null
            var bestScore = 0f

            // Stage A: Adaptive Canny
            val canny = Mat()
            try {
                val meanVal = Core.mean(blurred).`val`[0]
                val low = max(18.0, meanVal * 0.45)
                val high = min(220.0, max(low * 2.3, meanVal * 1.15))
                Imgproc.Canny(blurred, canny, low, high)
                Imgproc.morphologyEx(canny, canny, Imgproc.MORPH_CLOSE, kernel)
                findBestQuadInBinary(canny, small.width(), small.height(), 0.0f)?.let { (q, s) ->
                    if (s > bestScore) { bestQuad = q; bestScore = s }
                }
            } finally {
                canny.release()
            }

            // Stage B: Otsu Threshold
            if (bestScore < 0.72f) {
                val otsu = Mat()
                try {
                    Imgproc.threshold(blurred, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
                    Imgproc.morphologyEx(otsu, otsu, Imgproc.MORPH_CLOSE, kernel)
                    findBestQuadInBinary(otsu, small.width(), small.height(), -0.02f)?.let { (q, s) ->
                        if (s > bestScore) { bestQuad = q; bestScore = s }
                    }
                } finally {
                    otsu.release()
                }
            }

            bestQuad to bestScore
        } finally {
            kernel.release()
            blurred.release()
            small.release()
        }
    }

    private fun findBestQuadInBinary(
        binary: Mat,
        w: Int,
        h: Int,
        stageBonus: Float
    ): Pair<DocumentQuad, Float>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val imgArea = (w * h).toDouble()
        val minArea = imgArea * 0.08
        val maxArea = imgArea * 0.90 // Reject frame-hugging full-screen contours

        var bestQuad: DocumentQuad? = null
        var bestScore = 0f

        try {
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }.take(10)
            for (c in sorted) {
                val area = Imgproc.contourArea(c)
                if (area < minArea || area > maxArea) continue

                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(c2f, approx, 0.025 * peri, true)
                    val pts = approx.toArray()
                    if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                        val normPts = pts.map {
                            NormalizedPoint(
                                (it.x / w.toDouble()).toFloat().coerceIn(0f, 1f),
                                (it.y / h.toDouble()).toFloat().coerceIn(0f, 1f)
                            )
                        }
                        // Reject contours that hug 3 or more outer frame edges
                        val borderTouchCount = normPts.count { p ->
                            p.x <= 0.02f || p.x >= 0.98f || p.y <= 0.02f || p.y >= 0.98f
                        }
                        if (borderTouchCount >= 3) continue

                        val ordered = orderCorners(normPts)
                        val angleScore = scoreCornerAngles(ordered)
                        if (angleScore <= 0f) continue
                        val areaRatio = (area / imgArea).toFloat()
                        val areaScore = when {
                            areaRatio in 0.16f..0.78f -> 1.0f
                            else -> 0.72f
                        }
                        val score = (0.55f * angleScore + 0.45f * areaScore + stageBonus).coerceIn(0f, 1f)
                        if (score > bestScore) {
                            bestScore = score
                            bestQuad = ordered
                        }
                    }
                } finally {
                    approx.release()
                    c2f.release()
                }
            }
        } finally {
            contours.forEach { it.release() }
            hierarchy.release()
        }

        return bestQuad?.let { it to bestScore }
    }

    private fun orderCorners(pts: List<NormalizedPoint>): DocumentQuad {
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val sortedByDiff = pts.sortedBy { it.y - it.x }
        return DocumentQuad(
            topLeft = sortedBySum.first(),
            bottomRight = sortedBySum.last(),
            topRight = sortedByDiff.first(),
            bottomLeft = sortedByDiff.last()
        )
    }

    private fun scoreCornerAngles(q: DocumentQuad): Float {
        val pts = listOf(q.topLeft, q.topRight, q.bottomRight, q.bottomLeft)
        var maxDeviation = 0.0
        for (i in 0 until 4) {
            val prev = pts[(i + 3) % 4]
            val curr = pts[i]
            val next = pts[(i + 1) % 4]
            val v1x = prev.x - curr.x
            val v1y = prev.y - curr.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y
            val len1 = hypot(v1x.toDouble(), v1y.toDouble())
            val len2 = hypot(v2x.toDouble(), v2y.toDouble())
            if (len1 < 0.05 || len2 < 0.05) return 0f
            val cos = ((v1x * v2x + v1y * v2y) / (len1 * len2)).coerceIn(-1.0, 1.0)
            val deg = Math.toDegrees(acos(cos))
            if (deg < 45.0 || deg > 135.0) return 0f
            maxDeviation = max(maxDeviation, abs(deg - 90.0))
        }
        return (1.0 - maxDeviation / 50.0).toFloat().coerceIn(0.15f, 1.0f)
    }
}
