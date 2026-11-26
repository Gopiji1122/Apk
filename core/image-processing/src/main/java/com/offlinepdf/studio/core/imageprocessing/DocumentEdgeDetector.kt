package com.offlinepdf.studio.core.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
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
 * Tracks detected document quads across consecutive camera frames so Auto-Capture
 * triggers ONLY when the complete document is visible and held steady (not on a blind timer),
 * with a cooldown preventing accidental duplicate captures.
 */
class QuadStabilityTracker(
    private val requiredSteadyFrames: Int = 7,
    private val maxMovementDelta: Float = 0.032f,
    private val cooldownMillis: Long = 2400L
) {
    private var previousQuad: DocumentQuad? = null
    private var steadyCount: Int = 0
    private var lastTriggerMillis: Long = 0L

    data class StabilityState(
        val detectedQuad: DocumentQuad?,
        val progress: Float,
        val shouldAutoCapture: Boolean
    )

    fun onFrame(quad: DocumentQuad?, nowMillis: Long = System.currentTimeMillis()): StabilityState {
        if (quad == null) {
            previousQuad = null
            steadyCount = 0
            return StabilityState(null, 0f, false)
        }

        val prev = previousQuad
        if (prev != null && quad.maxCornerDelta(prev) <= maxMovementDelta) {
            steadyCount++
        } else {
            steadyCount = 1
        }
        previousQuad = quad

        val progress = (steadyCount.toFloat() / requiredSteadyFrames.toFloat()).coerceIn(0f, 1f)
        val cooldownReady = (nowMillis - lastTriggerMillis) >= cooldownMillis
        val trigger = steadyCount >= requiredSteadyFrames && cooldownReady
        if (trigger) {
            lastTriggerMillis = nowMillis
            steadyCount = 0
        }
        return StabilityState(quad, progress, trigger)
    }

    fun reset() {
        previousQuad = null
        steadyCount = 0
    }
}

@Singleton
class DocumentEdgeDetector @Inject constructor() {

    /**
     * Detects the largest 4-corner document boundary in a bitmap.
     * Operates on a downscaled Mat (~480px) so camera preview stays at 60 FPS.
     */
    fun detectDocumentQuad(bitmap: Bitmap): DocumentQuad? {
        if (!OpenCvInitializer.ensureInitialized()) return null

        val src = Mat()
        val resized = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val contours = mutableListOf<MatOfPoint>()

        return try {
            Utils.bitmapToMat(bitmap, src)
            val maxDim = maxOf(src.cols(), src.rows()).toDouble()
            val scale = if (maxDim > 480.0) 480.0 / maxDim else 1.0
            val targetSize = Size(src.cols() * scale, src.rows() * scale)
            Imgproc.resize(src, resized, targetSize)

            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            Imgproc.dilate(edges, dilated, kernel)

            Imgproc.findContours(
                dilated,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val totalArea = resized.cols().toDouble() * resized.rows().toDouble()
            val minArea = totalArea * 0.14
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
                                x = (p.x / resized.cols()).toFloat(),
                                y = (p.y / resized.rows()).toFloat()
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
            kernel.release()
            hierarchy.release()
            dilated.release()
            edges.release()
            blurred.release()
            gray.release()
            resized.release()
            src.release()
        }
    }
}
