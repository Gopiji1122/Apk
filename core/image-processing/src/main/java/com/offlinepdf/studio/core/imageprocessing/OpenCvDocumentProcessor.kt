package com.offlinepdf.studio.core.imageprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.offlinepdf.studio.core.common.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class OpenCvDocumentProcessor @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    /**
     * Applies 4-point perspective warp + 90-degree & fine rotation from a disk-backed raw capture
     * and writes the corrected result to [outputFile].
     */
    suspend fun warpAndRotate(
        rawFilePath: String,
        outputFile: File,
        quad: DocumentQuad,
        rotationDegrees: Int = 0,
        fineRotationDegrees: Float = 0f
    ): Boolean = withContext(defaultDispatcher) {
        if (!OpenCvInitializer.ensureInitialized()) return@withContext false
        val bitmap = decodeDownsampledBitmap(rawFilePath, maxSide = 2200) ?: return@withContext false

        val src = Mat()
        var warped = Mat()
        var rotated = Mat()
        var transform: Mat? = null
        var srcPts: MatOfPoint2f? = null
        var dstPts: MatOfPoint2f? = null

        try {
            Utils.bitmapToMat(bitmap, src)
            val w = src.cols().toDouble()
            val h = src.rows().toDouble()

            val tl = Point(quad.topLeft.x * w, quad.topLeft.y * h)
            val tr = Point(quad.topRight.x * w, quad.topRight.y * h)
            val br = Point(quad.bottomRight.x * w, quad.bottomRight.y * h)
            val bl = Point(quad.bottomLeft.x * w, quad.bottomLeft.y * h)

            val widthTop = hypot(tr.x - tl.x, tr.y - tl.y)
            val widthBottom = hypot(br.x - bl.x, br.y - bl.y)
            val maxW = max(widthTop, widthBottom).roundToInt().coerceIn(320, 2200)

            val heightLeft = hypot(bl.x - tl.x, bl.y - tl.y)
            val heightRight = hypot(br.x - tr.x, br.y - tr.y)
            val maxH = max(heightLeft, heightRight).roundToInt().coerceIn(320, 2800)

            srcPts = MatOfPoint2f(tl, tr, br, bl)
            dstPts = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((maxW - 1).toDouble(), 0.0),
                Point((maxW - 1).toDouble(), (maxH - 1).toDouble()),
                Point(0.0, (maxH - 1).toDouble())
            )

            transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            Imgproc.warpPerspective(src, warped, transform, Size(maxW.toDouble(), maxH.toDouble()))

            // Apply 90-degree rotation steps
            val normRot = ((rotationDegrees % 360) + 360) % 360
            when (normRot) {
                90 -> Core.rotate(warped, rotated, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(warped, rotated, Core.ROTATE_180)
                270 -> Core.rotate(warped, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> warped.copyTo(rotated)
            }

            // Apply optional fine rotation (-15..+15 deg)
            if (kotlin.math.abs(fineRotationDegrees) > 0.25f) {
                val center = Point(rotated.cols() / 2.0, rotated.rows() / 2.0)
                val rotMat = Imgproc.getRotationMatrix2D(center, -fineRotationDegrees.toDouble(), 1.0)
                val fineOut = Mat()
                try {
                    Imgproc.warpAffine(
                        rotated,
                        fineOut,
                        rotMat,
                        rotated.size(),
                        Imgproc.INTER_LINEAR,
                        Core.BORDER_CONSTANT,
                        Scalar(255.0, 255.0, 255.0, 255.0)
                    )
                    rotated.release()
                    rotated = fineOut
                } finally {
                    rotMat.release()
                }
            }

            saveMatAsJpeg(rotated, outputFile, quality = 94)
            true
        } catch (_: Throwable) {
            false
        } finally {
            bitmap.recycle()
            srcPts?.release()
            dstPts?.release()
            transform?.release()
            rotated.release()
            warped.release()
            src.release()
        }
    }

    /**
     * Applies any of the 10 professional enhancement modes on a disk-backed image
     * and writes the enhanced result to [outputFile].
     */
    suspend fun enhanceDocument(
        sourceFilePath: String,
        outputFile: File,
        mode: ScanFilterMode,
        custom: CustomAdjustments = CustomAdjustments()
    ): Boolean = withContext(defaultDispatcher) {
        if (!OpenCvInitializer.ensureInitialized()) return@withContext false
        if (mode == ScanFilterMode.ORIGINAL_COLOR) {
            File(sourceFilePath).copyTo(outputFile, overwrite = true)
            return@withContext true
        }

        val bitmap = decodeDownsampledBitmap(sourceFilePath, maxSide = 2200) ?: return@withContext false
        val rgba = Mat()
        val rgb = Mat()
        var resultRgb = Mat()

        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

            resultRgb = when (mode) {
                ScanFilterMode.ORIGINAL_COLOR -> rgb.clone()
                ScanFilterMode.AUTO_COLOR -> applyAutoColor(rgb)
                ScanFilterMode.DOCUMENT_COLOR -> applyDocumentColor(rgb)
                ScanFilterMode.ENHANCED_COLOR -> applyEnhancedColor(rgb)
                ScanFilterMode.BLACK_AND_WHITE -> applyBlackAndWhite(rgb)
                ScanFilterMode.GRAYSCALE -> applyGrayscale(rgb)
                ScanFilterMode.HIGH_CONTRAST -> applyHighContrast(rgb)
                ScanFilterMode.PHOTO_MODE -> applyPhotoMode(rgb)
                ScanFilterMode.CLEAN_DOCUMENT -> applyCleanDocument(rgb)
                ScanFilterMode.CUSTOM -> applyCustom(rgb, custom)
            }

            val outRgba = Mat()
            try {
                Imgproc.cvtColor(resultRgb, outRgba, Imgproc.COLOR_RGB2RGBA)
                saveMatAsJpeg(outRgba, outputFile, quality = 92)
            } finally {
                outRgba.release()
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            bitmap.recycle()
            resultRgb.release()
            rgb.release()
            rgba.release()
        }
    }

    private fun applyAutoColor(rgb: Mat): Mat {
        val lab = Mat()
        val channels = mutableListOf<Mat>()
        val merged = Mat()
        val out = Mat()
        val clahe = Imgproc.createCLAHE(2.2, Size(8.0, 8.0))
        return try {
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            clahe.apply(channels[0], channels[0])
            Core.merge(channels, merged)
            Imgproc.cvtColor(merged, out, Imgproc.COLOR_Lab2RGB)
            sharpenMat(out, 0.35f)
        } finally {
            channels.forEach { it.release() }
            merged.release()
            lab.release()
            out.release()
        }
    }

    /**
     * Illumination normalization & shadow removal while preserving colored ink & handwriting.
     */
    private fun applyDocumentColor(rgb: Mat): Mat {
        val channels = mutableListOf<Mat>()
        val normalized = mutableListOf<Mat>()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(21.0, 21.0))
        val merged = Mat()
        return try {
            Core.split(rgb, channels)
            for (ch in channels) {
                val dilated = Mat()
                val bg = Mat()
                val diff = Mat()
                try {
                    Imgproc.dilate(ch, dilated, kernel)
                    Imgproc.medianBlur(dilated, bg, 21)
                    Core.divide(ch, bg, diff, 255.0)
                    Core.normalize(diff, diff, 0.0, 255.0, Core.NORM_MINMAX)
                    normalized.add(diff.clone())
                } finally {
                    dilated.release()
                    bg.release()
                    diff.release()
                }
            }
            Core.merge(normalized, merged)
            sharpenMat(merged, 0.4f)
        } finally {
            channels.forEach { it.release() }
            normalized.forEach { it.release() }
            kernel.release()
            merged.release()
        }
    }

    private fun applyEnhancedColor(rgb: Mat): Mat {
        val auto = applyAutoColor(rgb)
        val hsv = Mat()
        val channels = mutableListOf<Mat>()
        val merged = Mat()
        val out = Mat()
        return try {
            Imgproc.cvtColor(auto, hsv, Imgproc.COLOR_RGB2HSV)
            Core.split(hsv, channels)
            channels[1].convertTo(channels[1], -1, 1.28, 0.0)
            Core.merge(channels, merged)
            Imgproc.cvtColor(merged, out, Imgproc.COLOR_HSV2RGB)
            sharpenMat(out, 0.55f)
        } finally {
            auto.release()
            channels.forEach { it.release() }
            merged.release()
            hsv.release()
            out.release()
        }
    }

    private fun applyBlackAndWhite(rgb: Mat): Mat {
        val gray = Mat()
        val blur = Mat()
        val bw = Mat()
        val out = Mat()
        return try {
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.GaussianBlur(gray, blur, Size(3.0, 3.0), 0.0)
            Imgproc.adaptiveThreshold(
                blur,
                bw,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                25,
                11.0
            )
            Imgproc.cvtColor(bw, out, Imgproc.COLOR_GRAY2RGB)
            out
        } finally {
            gray.release()
            blur.release()
            bw.release()
        }
    }

    private fun applyGrayscale(rgb: Mat): Mat {
        val gray = Mat()
        val enhanced = Mat()
        val out = Mat()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        return try {
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            clahe.apply(gray, enhanced)
            Imgproc.cvtColor(enhanced, out, Imgproc.COLOR_GRAY2RGB)
            out
        } finally {
            gray.release()
            enhanced.release()
        }
    }

    private fun applyHighContrast(rgb: Mat): Mat {
        val doc = applyDocumentColor(rgb)
        val out = Mat()
        return try {
            doc.convertTo(out, -1, 1.38, -28.0)
            out
        } finally {
            doc.release()
        }
    }

    private fun applyPhotoMode(rgb: Mat): Mat {
        val smooth = Mat()
        return try {
            Imgproc.bilateralFilter(rgb, smooth, 5, 45.0, 45.0)
            smooth.convertTo(smooth, -1, 1.06, 4.0)
            smooth
        } catch (_: Throwable) {
            smooth.release()
            rgb.clone()
        }
    }

    private fun applyCleanDocument(rgb: Mat): Mat {
        val doc = applyDocumentColor(rgb)
        val denoised = Mat()
        val out = Mat()
        return try {
            Imgproc.medianBlur(doc, denoised, 3)
            denoised.convertTo(out, -1, 1.15, 6.0)
            sharpenMat(out, 0.45f)
        } finally {
            doc.release()
            denoised.release()
            out.release()
        }
    }

    private fun applyCustom(rgb: Mat, custom: CustomAdjustments): Mat {
        val adjusted = Mat()
        val hsv = Mat()
        val channels = mutableListOf<Mat>()
        val merged = Mat()
        val satOut = Mat()
        return try {
            rgb.convertTo(adjusted, -1, custom.contrast.toDouble(), custom.brightness.toDouble())
            Imgproc.cvtColor(adjusted, hsv, Imgproc.COLOR_RGB2HSV)
            Core.split(hsv, channels)
            channels[1].convertTo(channels[1], -1, custom.saturation.toDouble(), 0.0)
            Core.merge(channels, merged)
            Imgproc.cvtColor(merged, satOut, Imgproc.COLOR_HSV2RGB)
            if (custom.sharpness > 0.05f) {
                sharpenMat(satOut, custom.sharpness)
            } else {
                satOut.clone()
            }
        } finally {
            adjusted.release()
            channels.forEach { it.release() }
            merged.release()
            hsv.release()
            satOut.release()
        }
    }

    private fun sharpenMat(src: Mat, amount: Float): Mat {
        val blurred = Mat()
        val sharp = Mat()
        return try {
            Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), 2.0)
            Core.addWeighted(src, 1.0 + amount, blurred, -amount.toDouble(), 0.0, sharp)
            sharp
        } finally {
            blurred.release()
        }
    }

    private fun saveMatAsJpeg(rgbaMat: Mat, outFile: File, quality: Int) {
        val outBmp = Bitmap.createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888)
        try {
            Utils.matToBitmap(rgbaMat, outBmp)
            outFile.parentFile?.mkdirs()
            val tmp = File(outFile.parentFile, "${outFile.name}.tmp")
            FileOutputStream(tmp).use { fos ->
                outBmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), fos)
            }
            if (outFile.exists()) outFile.delete()
            if (!tmp.renameTo(outFile)) {
                tmp.copyTo(outFile, overwrite = true)
                tmp.delete()
            }
        } finally {
            outBmp.recycle()
        }
    }

    fun decodeDownsampledBitmap(path: String, maxSide: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        val w = boundsOpts.outWidth
        val h = boundsOpts.outHeight
        if (w <= 0 || h <= 0) return null

        var sample = 1
        while ((w / sample) > maxSide || (h / sample) > maxSide) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, decodeOpts)
    }
}
