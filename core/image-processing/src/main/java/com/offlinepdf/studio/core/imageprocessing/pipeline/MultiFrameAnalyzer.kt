package com.offlinepdf.studio.core.imageprocessing.pipeline

import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-frame document analysis and safe shadow-reduction fusion:
 *  - Evaluates sharpness, exposure, and glare on each candidate frame.
 *  - Aligns secondary frames to the sharpest base frame via ORB + RANSAC homography.
 *  - Strictly aborts fusion if alignment inlier ratio < 0.45 or motion is unsafe (prevents ghosting/blur).
 *  - Selectively replaces moving shadow regions from brighter aligned pixels only.
 */
@Singleton
class MultiFrameAnalyzer @Inject constructor() {

    fun evaluateFrameQuality(rgbOrGray: Mat): FrameQualityReport {
        val gray = Mat()
        val small = Mat()
        val lap = Mat()
        val glareMask = Mat()
        val mean = MatOfDouble()
        val std = MatOfDouble()
        return try {
            if (rgbOrGray.channels() >= 3) {
                Imgproc.cvtColor(rgbOrGray, gray, Imgproc.COLOR_RGB2GRAY)
            } else {
                rgbOrGray.copyTo(gray)
            }
            val scale = min(1.0, 320.0 / max(gray.width(), gray.height()).toDouble())
            if (scale < 1.0) Imgproc.resize(gray, small, Size(gray.width() * scale, gray.height() * scale))
            else gray.copyTo(small)

            val meanLuma = Core.mean(small).`val`[0]
            Imgproc.Laplacian(small, lap, CvType.CV_64F)
            Core.meanStdDev(lap, mean, std)
            val s = std.toArray().firstOrNull() ?: 0.0
            val sharpVar = s * s

            Imgproc.threshold(small, glareMask, 250.0, 255.0, Imgproc.THRESH_BINARY)
            val glareRatio = Core.countNonZero(glareMask).toFloat() / (small.rows() * small.cols()).coerceAtLeast(1).toFloat()

            FrameQualityReport(
                sharpnessVariance = sharpVar,
                meanLuminance = meanLuma,
                glareRatio = glareRatio,
                isSharp = sharpVar >= 6.0,
                isWellExposed = meanLuma in 22.0..246.0,
                hasExcessiveGlare = glareRatio > 0.14f
            )
        } finally {
            std.release()
            mean.release()
            glareMask.release()
            lap.release()
            small.release()
            gray.release()
        }
    }

    /**
     * Safely fuses up to 3 burst frames to reduce moving shadows.
     * Falls back immediately to the sharpest single frame whenever camera motion risks ghosting.
     */
    fun fuseAlignedFramesSafely(framesRgb: List<Mat>): Mat {
        require(framesRgb.isNotEmpty()) { "At least one frame is required" }
        if (framesRgb.size == 1) return framesRgb.first().clone()

        val scored = framesRgb.map { it to evaluateFrameQuality(it) }
            .sortedByDescending { it.second.compositeScore }
        val base = scored.first().first
        val baseGray = Mat()
        Imgproc.cvtColor(base, baseGray, Imgproc.COLOR_RGB2GRAY)

        val fused = base.clone()
        val orb = ORB.create(600)
        val matcher = BFMatcher.create(Core.NORM_HAMMING, true)
        val kpBase = MatOfKeyPoint()
        val descBase = Mat()

        try {
            orb.detectAndCompute(baseGray, Mat(), kpBase, descBase)
            if (descBase.empty()) return fused

            for ((cand, _) in scored.drop(1).take(2)) {
                val candGray = Mat()
                val kpCand = MatOfKeyPoint()
                val descCand = Mat()
                val matches = MatOfDMatch()
                val inlierMask = Mat()
                val aligned = Mat()
                try {
                    Imgproc.cvtColor(cand, candGray, Imgproc.COLOR_RGB2GRAY)
                    orb.detectAndCompute(candGray, Mat(), kpCand, descCand)
                    if (descCand.empty()) continue

                    matcher.match(descCand, descBase, matches)
                    val good = matches.toList().sortedBy { it.distance }.take(60)
                    if (good.size < 16) continue

                    val baseList = kpBase.toList()
                    val candList = kpCand.toList()
                    val srcPts = MatOfPoint2f(*good.map { candList[it.queryIdx].pt }.toTypedArray())
                    val dstPts = MatOfPoint2f(*good.map { baseList[it.trainIdx].pt }.toTypedArray())
                    val hMat = Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, 2.5, inlierMask)
                    srcPts.release()
                    dstPts.release()

                    if (hMat.empty()) continue
                    val inliers = Core.countNonZero(inlierMask)
                    val inlierRatio = inliers.toFloat() / good.size.toFloat()
                    if (inlierRatio < 0.48f) {
                        hMat.release()
                        continue
                    }

                    Imgproc.warpPerspective(cand, aligned, hMat, base.size(), Imgproc.INTER_LINEAR)
                    hMat.release()

                    // Selective shadow-only bright replacement: only lift pixels that are shadowed in base
                    // without blurring sharp dark text strokes
                    val alignedGray = Mat()
                    val diff = Mat()
                    val shadowMask = Mat()
                    try {
                        Imgproc.cvtColor(aligned, alignedGray, Imgproc.COLOR_RGB2GRAY)
                        Core.subtract(alignedGray, baseGray, diff)
                        // Moving shadow region: aligned is significantly brighter (+28..+90) than base
                        Imgproc.threshold(diff, shadowMask, 28.0, 255.0, Imgproc.THRESH_BINARY)
                        val shadowRatio = Core.countNonZero(shadowMask).toFloat() / (base.rows() * base.cols()).toFloat()
                        if (shadowRatio in 0.01f..0.45f) {
                            aligned.copyTo(fused, shadowMask)
                        }
                    } finally {
                        shadowMask.release()
                        diff.release()
                        alignedGray.release()
                    }
                } finally {
                    aligned.release()
                    inlierMask.release()
                    matches.release()
                    descCand.release()
                    kpCand.release()
                    candGray.release()
                }
            }
            return fused
        } finally {
            descBase.release()
            kpBase.release()
            baseGray.release()
        }
    }
}
