package com.offlinepdf.studio.core.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Adobe Scan-style temporal smoother & stability tracker:
 * - Smooths corners across frames (EMA) so the detection box glides smoothly without jitter.
 * - Tolerates up to 2 dropped frames so the overlay never blinks/flickers.
 * - Triggers Auto-Capture only when the document is held steady, with cooldown protection.
 */
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
        val shouldAutoCapture: Boolean
    )

    fun onFrame(rawQuad: DocumentQuad?, nowMillis: Long = System.currentTimeMillis()): StabilityState {
        if (rawQuad == null) {
            missedCount++
            if (missedCount > maxMissedFrames) {
                smoothedQuad = null
                steadyCount = 0
                return StabilityState(null, 0f, false)
            }
            val progress = (steadyCount.toFloat() / requiredSteadyFrames.toFloat()).coerceIn(0f, 1f)
            return StabilityState(smoothedQuad, progress, false)
        }

        missedCount = 0
        val prev = smoothedQuad
        val nextSmoothed = if (prev == null) rawQuad else prev.lerp(rawQuad, 0.38f)

        if (prev != null && rawQuad.maxCornerDelta(prev) <= maxMovementDelta) {
            steadyCount++
        } else {
            steadyCount = 1
        }
        smoothedQuad = nextSmoothed

        val progress = (steadyCount.toFloat() / requiredSteadyFrames.toFloat()).coerceIn(0f, 1f)
        val cooldownReady = (nowMillis - lastTriggerMillis) >= cooldownMillis
        val trigger = steadyCount >= requiredSteadyFrames && cooldownReady
        if (trigger) {
            lastTriggerMillis = nowMillis
            steadyCount = 0
        }
        return StabilityState(nextSmoothed, progress, trigger)
    }

    fun reset() {
        smoothedQuad = null
        steadyCount = 0
        missedCount = 0
    }
}

@Singleton
class DocumentEdgeDetector @Inject constructor() {

    /**
     * Detects the largest 4-corner document boundary in a camera or file bitmap.
     * Applies [rotationDegrees] (e.g. 90 on portrait OnePlus rear camera) BEFORE contour
     * detection so normalized coordinates always match the portrait preview 1:1.
     */
    fun detectDocumentQuad(bitmap: Bitmap, rotationDegrees: Int = 0): DocumentQuad? {
        if (!OpenCvInitializer.ensureInitialized()) return null

        val src = Mat()
        val resized = Mat()
        val upright = Mat()
        val gray = Mat()
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
            // Morphological close suppresses interior text so outer paper boundary dominates
            Imgproc.morphologyEx(gray, closed, Imgproc.MORPH_CLOSE, closeKernel)
            Imgproc.GaussianBlur(closed, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 40.0, 130.0)
            Imgproc.dilate(edges, dilated, dilateKernel)

            Imgproc.findContours(
                dilated,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
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
                    if (pts.size == 4) {
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
            bestQuad
        } catch (_: Throwable) {
            null
        } finally {
            contours.forEach { it.release() }
            dilateKernel.release()
            closeKernel.release()
            hierarchy.release()
            dilated.release()
            edges.release()
            blurred.release()
            closed.release()
            gray.release()
            upright.release()
            resized.release()
            src.release()
        }
    }
}
