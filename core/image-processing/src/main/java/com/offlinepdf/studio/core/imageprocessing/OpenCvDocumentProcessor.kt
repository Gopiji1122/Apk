package com.offlinepdf.studio.core.imageprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.offlinepdf.studio.core.common.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class OpenCvDocumentProcessor @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    /**
     * Reads the EXIF orientation tag written by Android cameras (e.g. OnePlus rear camera
     * stores portrait captures rotated 90° in landscape) and physically bakes the upright
     * orientation into the JPEG so the document is always upright portrait and never stretched.
     */
    suspend fun normalizeExifOrientation(filePath: String, maxSide: Int = 2800): Boolean =
        withContext(defaultDispatcher) {
            runCatching {
                val exif = ExifInterface(filePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                var needsTransform = true
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f)
                        matrix.postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f)
                        matrix.postScale(-1f, 1f)
                    }
                    else -> needsTransform = false
                }
                if (!needsTransform) return@runCatching true

                val src = decodeDownsampledBitmap(filePath, maxSide = maxSide) ?: return@runCatching false
                val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
                try {
                    saveBitmapAsJpeg(rotated, File(filePath), quality = 95)
                    true
                } finally {
                    if (rotated !== src) rotated.recycle()
                    src.recycle()
                }
            }.getOrDefault(false)
        }

    /**
     * Physically rotates a disk-backed image 90 degrees clockwise so the Crop screen
     * updates upright immediately without any aspect-ratio distortion.
     */
    suspend fun rotateFile90Clockwise(filePath: String, maxSide: Int = 2800): Boolean =
        withContext(defaultDispatcher) {
            runCatching {
                val src = decodeDownsampledBitmap(filePath, maxSide = maxSide) ?: return@runCatching false
                val matrix = Matrix().apply { postRotate(90f) }
                val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
                try {
                    saveBitmapAsJpeg(rotated, File(filePath), quality = 95)
                    true
                } finally {
                    if (rotated !== src) rotated.recycle()
                    src.recycle()
                }
            }.getOrDefault(false)
        }

    suspend fun warpAndRotate(
        rawFilePath: String,
        outputFile: File,
        quad: DocumentQuad,
        rotationDegrees: Int = 0,
        fineRotationDegrees: Float = 0f,
        qualityMode: ScanQualityMode = ScanQualityMode.FAST
    ): Boolean = withContext(defaultDispatcher) {
        if (!OpenCvInitializer.ensureInitialized()) return@withContext false
        val maxSide = qualityMode.maxProcessSide
        val bitmap = decodeDownsampledBitmap(rawFilePath, maxSide = maxSide) ?: return@withContext false

        val src = Mat()
        val warped = Mat()
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
            val maxW = max(widthTop, widthBottom).roundToInt().coerceIn(320, maxSide)

            val heightLeft = hypot(bl.x - tl.x, bl.y - tl.y)
            val heightRight = hypot(br.x - tr.x, br.y - tr.y)
            val maxH = max(heightLeft, heightRight).roundToInt().coerceIn(320, (maxSide * 1.4).toInt())

            srcPts = MatOfPoint2f(tl, tr, br, bl)
            dstPts = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((maxW - 1).toDouble(), 0.0),
                Point((maxW - 1).toDouble(), (maxH - 1).toDouble()),
                Point(0.0, (maxH - 1).toDouble())
            )

            transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            Imgproc.warpPerspective(src, warped, transform, Size(maxW.toDouble(), maxH.toDouble()))

            val normRot = ((rotationDegrees % 360) + 360) % 360
            when (normRot) {
                90 -> Core.rotate(warped, rotated, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(warped, rotated, Core.ROTATE_180)
                270 -> Core.rotate(warped, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> warped.copyTo(rotated)
            }

            if (abs(fineRotationDegrees) > 0.25f) {
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

            saveMatAsJpeg(rotated, outputFile, quality = qualityMode.jpegQuality)
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

    suspend fun enhanceDocument(
        sourceFilePath: String,
        outputFile: File,
        mode: ScanFilterMode,
        custom: CustomAdjustments = CustomAdjustments(),
        qualityMode: ScanQualityMode = ScanQualityMode.FAST
    ): Boolean = withContext(defaultDispatcher) {
        if (!OpenCvInitializer.ensureInitialized()) return@withContext false
        if (mode == ScanFilterMode.ORIGINAL_COLOR) {
            File(sourceFilePath).copyTo(outputFile, overwrite = true)
            return@withContext true
        }

        val bitmap = decodeDownsampledBitmap(sourceFilePath, maxSide = qualityMode.maxProcessSide)
            ?: return@withContext false
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
                saveMatAsJpeg(outRgba, outputFile, quality = qualityMode.jpegQuality)
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

    private fun applyDocumentColor(rgb: Mat): Mat {
        val channels = mutableListOf<Mat>()
        val normalized = mutableListOf<Mat>()
        val smallKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
        val merged = Mat()
        return try {
            Core.split(rgb, channels)
            for (ch in channels) {
                // Estimate the page background on a 1/4-scale copy: ~10x faster, visually identical.
                val small = Mat()
                val dilated = Mat()
                val bgSmall = Mat()
                val bg = Mat()
                val diff = Mat()
                try {
                    Imgproc.resize(ch, small, Size(), 0.25, 0.25, Imgproc.INTER_AREA)
                    Imgproc.dilate(small, dilated, smallKernel)
                    Imgproc.medianBlur(dilated, bgSmall, 7)
                    Imgproc.resize(bgSmall, bg, ch.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
                    Core.divide(ch, bg, diff, 255.0)
                    Core.normalize(diff, diff, 0.0, 255.0, Core.NORM_MINMAX)
                    normalized.add(diff.clone())
                } finally {
                    small.release()
                    dilated.release()
                    bgSmall.release()
                    bg.release()
                    diff.release()
                }
            }
            Core.merge(normalized, merged)
            sharpenMat(merged, 0.4f)
        } finally {
            channels.forEach { it.release() }
            normalized.forEach { it.release() }
            smallKernel.release()
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
                blur, bw, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 25, 11.0
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
            if (custom.sharpness > 0.05f) sharpenMat(satOut, custom.sharpness) else satOut.clone()
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
            saveBitmapAsJpeg(outBmp, outFile, quality)
        } finally {
            outBmp.recycle()
        }
    }

    private fun saveBitmapAsJpeg(bitmap: Bitmap, outFile: File, quality: Int) {
        outFile.parentFile?.mkdirs()
        val tmp = File(outFile.parentFile, "${outFile.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), fos)
        }
        if (outFile.exists()) outFile.delete()
        if (!tmp.renameTo(outFile)) {
            tmp.copyTo(outFile, overwrite = true)
            tmp.delete()
        }
    }

    /**
     * Decodes with the cheapest power-of-two sample that stays at or above [maxSide], then scales
     * to exactly [maxSide]. (The previous version could land well BELOW maxSide, e.g. a 4000px photo
     * decoded at 2000px even in HD mode.)
     */
    fun decodeDownsampledBitmap(path: String, maxSide: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        val w = boundsOpts.outWidth
        val h = boundsOpts.outHeight
        if (w <= 0 || h <= 0) return null

        var sample = 1
        while (max(w, h) / (sample * 2) >= maxSide) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, decodeOpts) ?: return null
        val longest = max(decoded.width, decoded.height)
        if (longest <= maxSide) return decoded
        val ratio = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).roundToInt().coerceAtLeast(1),
            (decoded.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
