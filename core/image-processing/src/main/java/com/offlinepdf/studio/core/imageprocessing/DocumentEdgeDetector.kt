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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class FrameDetectionResult(
    val quad: DocumentQuad?,
    val sharpnessScore: Double = 100.0,
    val meanLuminance: Double = 128.0
) {
    val isSharpEnough: Boolean get() = sharpnessScore >= 32.0
    val isWellExposed: Boolean get() = meanLuminance in 32.0..242.0
}

class QuadStabilityTracker(
    private val requiredSteadyFrames: Int = 6,
    private val maxMovementDelta: Float = 0.035f,
    private val cooldownMillis: Long = 2500L,
    private val maxMissedFrames: Int = 2
) {
    private var smoothedQuad: DocumentQuad? = null
    private var steadyCount: Int = 0
    private var missedCount: Int = 0
    private var lastTriggerMillis: Long = 0L

    data class StabilityState(
        val detectedQuad: DocumentQuad?,
        val progress: Float,
        val shouldAutoCapture: Boolean,
        val qualityHint: String? = null
    )

    fun onFrame(rawQuad: DocumentQuad?, nowMillis: Long = System.currentTimeMillis()): StabilityState {
        return onFrameEvaluated(FrameDetectionResult(rawQuad), nowMillis)
    }

    fun onFrameEvaluated(
        result: FrameDetectionResult,
        nowMillis: Long = System.currentTimeMillis()
    ): StabilityState {
        val rawQuad = result.quad
        if (rawQuad == null) {
            missedCount++
            if (missedCount > maxMissedFrames) {
                smoothedQuad = null
                steadyCount = 0
                return StabilityState(null, 0f, false, null)
            }
            val progress = (steadyCount.toFloat() / requiredSteadyFrames.toFloat()).coerceIn(0f, 1f)
            return StabilityState(smoothedQuad, progress, false, null)
        }

        missedCount = 0
        val prev = smoothedQuad
        val nextSmoothed = if (prev == null) rawQuad else prev.lerp(rawQuad, 0.38f)
        smoothedQuad = nextSmoothed

        // Reject blurry or badly exposed frames from triggering auto-capture
        if (!result.isSharpEnough) {
            steadyCount = (steadyCount - 1).coerceAtLeast(0)
            return StabilityState(nextSmoothed, steadyCount.toFloat() / requiredSteadyFrames, false, "Focusing — hold still")
        }
        if (!result.isWellExposed) {
            steadyCount = (steadyCount - 1).coerceAtLeast(0)
            val hint = if (result.meanLuminance < 32.0) "Too dark — turn on flash" else "Too bright — reduce glare"
            return StabilityState(nextSmoothed, steadyCount.toFloat() / requiredSteadyFrames, false, hint)
        }

        if (prev != null && rawQuad.maxCornerDelta(prev) <= maxMovementDelta) {
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
        return StabilityState(nextSmoothed, progress, trigger, null)
    }

    fun reset() {
        smoothedQuad = null
        steadyCount = 0
        missedCount = 0
    }
}

@Singleton
class DocumentEdgeDetector @Inject constructor() {

    fun detectDocumentQuad(bitmap: Bitmap, rotationDegrees: Int = 0): DocumentQuad? =
        analyzeFrame(bitmap, rotationDegrees).quad

    /**
     * Detects 4-corner document quad + computes Laplacian sharpness & luminance exposure
     * on a lightweight downscaled frame for sharp, reliable Auto-Capture.
     */
    fun analyzeFrame(bitmap: Bitmap, rotationDegrees: Int = 0): FrameDetectionResult {
        if (!OpenCvInitializer.ensureInitialized()) return FrameDetectionResult(null)

        val src = Mat()
        val resized = Mat()
        val upright = Mat()
        val gray = Mat()
        val laplacian = Mat()
        val meanMat = MatOfDouble()
        val stdMat = MatOfDouble()
        val lumMean = MatOfDouble()
        val lumStd = MatOfDouble()
        val closed = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val hierarchy = Mat()
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val contours = mutableListOf<MatOfPoint>()

        return try {
            Utils.bitmapToMat(bitmap, src)
            val maxDim = maxOf(src.cols(), src.rows()).toDouble()
            val scale = if (maxDim > 420.0) 420.0 / maxDim else 1.0
            Imgproc.resize(src, resized, Size(src.cols() * scale, src.rows() * scale))

            when (((rotationDegrees % 360) + 360) % 360) {
                90 -> Core.rotate(resized, upright, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(resized, upright, Core.ROTATE_180)
                270 -> Core.rotate(resized, upright, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> resized.copyTo(upright)
            }

            Imgproc.cvtColor(upright, gray, Imgproc.COLOR_RGBA2GRAY)

            // Exposure & Laplacian variance blur metrics
            Core.meanStdDev(gray, lumMean, lumStd)
            val meanLuminance = lumMean.toArray().firstOrNull() ?: 128.0

            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, meanMat, stdMat)
            val stdVal = stdMat.toArray().firstOrNull() ?: 10.0
            val sharpnessVariance = stdVal * stdVal

            Imgproc.morphologyEx(gray, closed, Imgproc.MORPH_CLOSE, closeKernel)
            Imgproc.GaussianBlur(closed, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 40.0, 130.0)
            Imgproc.dilate(edges, dilated, dilateKernel)

            Imgproc.findContours(
                dilated, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )

            val totalArea = upright.cols().toDouble() * upright.rows().toDouble()
            val minArea = totalArea * 0.16
            val maxArea = totalArea * 0.96

            var bestQuad: DocumentQuad? = null
            var bestArea = 0.0

            val sorted = contours.sortedByDescending { abs(Imgproc.contourArea(it)) }.take(8)
            for (contour in sorted) {
                val area = abs(Imgproc.contourArea(contour))
                if (area < minArea || area > maxArea || area <= bestArea) continue

                val contour2f = MatOfPoint2f()
                val approx = MatOfPoint2f()
                try {
                    contour.convertTo(contour2f, CvType.CV_32FC2)
                    val peri = Imgproc.arcLength(contour2f, true)
                    Imgproc.approxPolyDP(contour2f, approx, 0.025 * peri, true)
                    val pts = approx.toArray()
                    val convex = pts.size == 4 && MatOfPoint(*pts).let { m ->
                        try { Imgproc.isContourConvex(m) } finally { m.release() }
                    }
                    if (pts.size == 4 && convex) {
                        val normPts = pts.map { p ->
                            NormalizedPoint(
                                x = (p.x / upright.cols()).toFloat(),
                                y = (p.y / upright.rows()).toFloat()
                            )
                        }
                        bestQuad = DocumentQuad.fromUnordered(normPts)
                        bestArea = area
                    }
                } finally {
                    contour2f.release()
                    approx.release()
                }
            }
            FrameDetectionResult(bestQuad, sharpnessVariance, meanLuminance)
        } catch (_: Throwable) {
            FrameDetectionResult(null)
        } finally {
            contours.forEach { it.release() }
            dilateKernel.release()
            closeKernel.release()
            hierarchy.release()
            dilated.release()
            edges.release()
            blurred.release()
            closed.release()
            lumStd.release()
            lumMean.release()
            stdMat.release()
            meanMat.release()
            laplacian.release()
            gray.release()
            upright.release()
            resized.release()
            src.release()
        }
    }
}
