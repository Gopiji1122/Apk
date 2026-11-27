package com.offlinepdf.studio.core.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
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
    val meanLuminance: Double = 128.0
) {
    val isSharpEnough: Boolean get() = sharpnessScore >= 5.0
    val isWellExposed: Boolean get() = meanLuminance in 20.0..248.0
}

class QuadStabilityTracker(
    private val requiredSteadyFrames: Int = 4,
    private val maxMovementDelta: Float = 0.065f,
    private val cooldownMillis: Long = 2400L,
    private val maxMissedFrames: Int = 3
) {
    private var smoothedQuad: DocumentQuad? = null
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
            "HIGH" -> 0.40f
            "LOW" -> 0.68f
            else -> 0.52f
        }
        val effectiveDelta = when (sensitivity) {
            "HIGH" -> 0.08f
            "LOW" -> 0.045f
            else -> maxMovementDelta
        }

        if (rawQuad == null || result.confidence < minConf) {
            missedCount++
            if (missedCount > maxMissedFrames) {
                smoothedQuad = null
                steadyCount = 0
                return StabilityState(null, 0f, 0f, false, null)
            }
            val progress = (steadyCount.toFloat() / requiredSteadyFrames.toFloat()).coerceIn(0f, 1f)
            return StabilityState(smoothedQuad, result.confidence, progress, false, null)
        }

        missedCount = 0
        val prev = smoothedQuad
        val nextSmoothed = if (prev == null) rawQuad else prev.lerp(rawQuad, 0.42f)
        smoothedQuad = nextSmoothed

        if (!result.isSharpEnough) {
            steadyCount = (steadyCount - 1).coerceAtLeast(0)
            return StabilityState(nextSmoothed, result.confidence, steadyCount.toFloat() / requiredSteadyFrames, false, "Focusing — hold still")
        }
        if (!result.isWellExposed) {
            steadyCount = (steadyCount - 1).coerceAtLeast(0)
            val hint = if (result.meanLuminance < 20.0) "Low light — try flash" else "Too bright — tilt slightly"
            return StabilityState(nextSmoothed, result.confidence, steadyCount.toFloat() / requiredSteadyFrames, false, hint)
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
        return StabilityState(nextSmoothed, result.confidence, progress, trigger, null)
    }

    fun reset() {
        smoothedQuad = null
        steadyCount = 0
        missedCount = 0
    }
}

@Singleton
class DocumentEdgeDetector @Inject constructor() {

    // Reusable buffer for zero-allocation Y-plane extraction during live camera analysis
    @Volatile
    private var reusableYBuffer: ByteArray = ByteArray(0)

    fun detectDocumentQuad(bitmap: Bitmap, rotationDegrees: Int = 0): DocumentQuad? =
        analyzeFrame(bitmap, rotationDegrees).quad

    /**
     * Zero-Bitmap live camera analysis directly from CameraX YUV_420_888 Y (luminance) plane!
     * Eliminates YUV->RGB conversion and Bitmap GC pressure for smooth 60 FPS preview.
     */
    fun analyzeYPlane(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        rotationDegrees: Int
    ): FrameDetectionResult {
        if (!OpenCvInitializer.ensureInitialized()) return FrameDetectionResult(null)
        val required = width * height
        if (reusableYBuffer.size != required) {
            reusableYBuffer = ByteArray(required)
        }
        val out = reusableYBuffer
        val dup = yBuffer.duplicate()
        dup.rewind()
        if (rowStride == width) {
            dup.get(out, 0, min(required, dup.remaining()))
        } else {
            var dstOff = 0
            for (row in 0 until height) {
                val srcPos = row * rowStride
                if (srcPos + width <= dup.limit()) {
                    dup.position(srcPos)
                    dup.get(out, dstOff, width)
                }
                dstOff += width
            }
        }

        val grayFull = Mat(height, width, CvType.CV_8UC1)
        return try {
            grayFull.put(0, 0, out)
            analyzeGrayMat(grayFull, rotationDegrees)
        } catch (_: Throwable) {
            FrameDetectionResult(null)
        } finally {
            grayFull.release()
        }
    }

    fun analyzeFrame(bitmap: Bitmap, rotationDegrees: Int = 0): FrameDetectionResult {
        if (!OpenCvInitializer.ensureInitialized()) return FrameDetectionResult(null)
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            analyzeGrayMat(gray, rotationDegrees)
        } catch (_: Throwable) {
            FrameDetectionResult(null)
        } finally {
            gray.release()
            rgba.release()
        }
    }

    /**
     * 3-Stage OpenCV Document Detection Pipeline:
     * Stage 1: Adaptive Canny on morphologically closed grayscale
     * Stage 2: Otsu threshold fallback (white/colored paper on medium/dark desks)
     * Stage 3: Adaptive Gaussian threshold fallback (white paper on light backgrounds)
     * Evaluates area, convexity, aspect ratio, and interior corner angles (55°..125°).
     */
    private fun analyzeGrayMat(grayFull: Mat, rotationDegrees: Int): FrameDetectionResult {
        val resized = Mat()
        val upright = Mat()
        val laplacian = Mat()
        val meanMat = MatOfDouble()
        val stdMat = MatOfDouble()
        val lumMean = MatOfDouble()
        val lumStd = MatOfDouble()
        val closed = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val otsu = Mat()
        val adaptive = Mat()
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

        return try {
            val maxDim = max(grayFull.cols(), grayFull.rows()).toDouble()
            val scale = if (maxDim > 380.0) 380.0 / maxDim else 1.0
            Imgproc.resize(grayFull, resized, Size(grayFull.cols() * scale, grayFull.rows() * scale))

            when (((rotationDegrees % 360) + 360) % 360) {
                90 -> Core.rotate(resized, upright, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(resized, upright, Core.ROTATE_180)
                270 -> Core.rotate(resized, upright, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> resized.copyTo(upright)
            }

            Core.meanStdDev(upright, lumMean, lumStd)
            val meanLuminance = lumMean.toArray().firstOrNull() ?: 128.0

            Imgproc.Laplacian(upright, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, meanMat, stdMat)
            val stdVal = stdMat.toArray().firstOrNull() ?: 10.0
            val sharpnessVariance = stdVal * stdVal

            Imgproc.morphologyEx(upright, closed, Imgproc.MORPH_CLOSE, closeKernel)
            Imgproc.GaussianBlur(closed, blurred, Size(5.0, 5.0), 0.0)

            // Stage 1: Adaptive Canny
            val lowT = (meanLuminance * 0.35).coerceIn(15.0, 50.0)
            val highT = (meanLuminance * 1.1).coerceIn(60.0, 150.0)
            Imgproc.Canny(blurred, edges, lowT, highT)
            Imgproc.dilate(edges, dilated, dilateKernel)

            var best = findBestScoredQuad(dilated, upright.cols(), upright.rows())

            // Stage 2: Otsu threshold fallback
            if (best == null || best.second < 0.60f) {
                Imgproc.threshold(blurred, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                val cand = findBestScoredQuad(otsu, upright.cols(), upright.rows())
                if (cand != null && (best == null || cand.second > best.second)) {
                    best = cand
                }
            }

            // Stage 3: Adaptive threshold fallback for low-contrast / light desks
            if (best == null || best.second < 0.55f) {
                Imgproc.adaptiveThreshold(
                    blurred, adaptive, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 4.0
                )
                Imgproc.dilate(adaptive, adaptive, dilateKernel)
                val cand = findBestScoredQuad(adaptive, upright.cols(), upright.rows())
                if (cand != null && (best == null || cand.second > best.second)) {
                    best = cand
                }
            }

            FrameDetectionResult(
                quad = best?.first,
                confidence = best?.second ?: 0f,
                sharpnessScore = sharpnessVariance,
                meanLuminance = meanLuminance
            )
        } finally {
            dilateKernel.release()
            closeKernel.release()
            adaptive.release()
            otsu.release()
            dilated.release()
            edges.release()
            blurred.release()
            closed.release()
            lumStd.release()
            lumMean.release()
            stdMat.release()
            meanMat.release()
            laplacian.release()
            upright.release()
            resized.release()
        }
    }

    private fun findBestScoredQuad(binaryMat: Mat, frameW: Int, frameH: Int): Pair<DocumentQuad, Float>? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        return try {
            Imgproc.findContours(
                binaryMat, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
            val totalArea = frameW.toDouble() * frameH.toDouble()
            val minArea = totalArea * 0.12
            val maxArea = totalArea * 0.96

            var bestPair: Pair<DocumentQuad, Float>? = null

            val sorted = contours.sortedByDescending { abs(Imgproc.contourArea(it)) }.take(10)
            for (contour in sorted) {
                val area = abs(Imgproc.contourArea(contour))
                if (area < minArea || area > maxArea) continue

                val contour2f = MatOfPoint2f()
                val approx = MatOfPoint2f()
                try {
                    contour.convertTo(contour2f, CvType.CV_32FC2)
                    val peri = Imgproc.arcLength(contour2f, true)
                    Imgproc.approxPolyDP(contour2f, approx, 0.03 * peri, true)
                    val pts = approx.toArray()
                    if (pts.size == 4) {
                        val normPts = pts.map { p ->
                            NormalizedPoint((p.x / frameW).toFloat(), (p.y / frameH).toFloat())
                        }
                        val quad = DocumentQuad.fromUnordered(normPts)
                        val score = scoreQuadGeometry(quad, (area / totalArea).toFloat())
                        if (score > 0.35f && (bestPair == null || score > bestPair.second)) {
                            bestPair = quad to score
                        }
                    }
                } finally {
                    contour2f.release()
                    approx.release()
                }
            }
            bestPair
        } finally {
            contours.forEach { it.release() }
            hierarchy.release()
        }
    }

    private fun scoreQuadGeometry(q: DocumentQuad, areaRatio: Float): Float {
        val pts = q.asList()
        // Check all 4 interior corner angles are reasonable (50°..130°)
        var angleScore = 1f
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
            if (len1 < 0.08 || len2 < 0.08) return 0f
            val cosA = ((v1x * v2x + v1y * v2y) / (len1 * len2)).coerceIn(-1.0, 1.0)
            val deg = Math.toDegrees(acos(cosA))
            if (deg < 48.0 || deg > 135.0) return 0f
            val deviation = abs(90.0 - deg).toFloat() / 45f
            angleScore = min(angleScore, (1f - deviation * 0.45f).coerceIn(0.4f, 1f))
        }
        val areaScore = (areaRatio / 0.55f).coerceIn(0.45f, 1f)
        return (0.65f * angleScore + 0.35f * areaScore).coerceIn(0f, 1f)
    }
}
