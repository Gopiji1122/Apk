package com.offlinepdf.studio.core.imageprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.exifinterface.media.ExifInterface
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class CleanupBrushMode {
    SURROUNDING_COLOR,
    CUSTOM_COLOR
}

data class CleanupStroke(
    val points: List<NormalizedPoint>,
    val brushRadiusNorm: Float = 0.032f,
    val mode: CleanupBrushMode = CleanupBrushMode.SURROUNDING_COLOR,
    val customColorArgb: Int = Color.WHITE
)

data class MarkupStroke(
    val points: List<NormalizedPoint>,
    val strokeWidthNorm: Float = 0.008f,
    val colorArgb: Int = Color.RED,
    val isHighlighter: Boolean = false
)

@Singleton
class OpenCvDocumentProcessor @Inject constructor() {

    fun normalizeExifOrientation(filePath: String, maxSide: Int = 3200): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        val exif = runCatching { ExifInterface(filePath) }.getOrNull() ?: return true
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) return true

        val src = decodeDownsampledBitmap(filePath, maxSide) ?: return false
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated !== src) src.recycle()
        return try {
            saveBitmapAsJpeg(rotated, file, 94)
            runCatching {
                val outExif = ExifInterface(filePath)
                outExif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                outExif.saveAttributes()
            }
            true
        } finally {
            rotated.recycle()
        }
    }

    fun rotateFile90(filePath: String, clockwise: Boolean = true, maxSide: Int = 3200): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        val src = decodeDownsampledBitmap(filePath, maxSide) ?: return false
        val matrix = Matrix().apply { postRotate(if (clockwise) 90f else -90f) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated !== src) src.recycle()
        return try {
            saveBitmapAsJpeg(rotated, file, 94)
            true
        } finally {
            rotated.recycle()
        }
    }

    fun rotateFile90Clockwise(filePath: String, maxSide: Int = 3200): Boolean =
        rotateFile90(filePath, clockwise = true, maxSide = maxSide)

    /**
     * Perspective warp + rotation with real Fast vs HD quality differentiation:
     * - FAST: 1800px max side, INTER_LINEAR, JPEG 86% (ultra-fast).
     * - HD:   3200px max side, INTER_CUBIC, subtle edge-preserving denoise + fine sharpening, JPEG 95%.
     */
    fun warpAndRotate(
        rawFilePath: String,
        outputFile: File,
        quad: DocumentQuad,
        rotationDegrees: Int = 0,
        fineRotationDegrees: Float = 0f,
        qualityMode: ScanQualityMode = ScanQualityMode.FAST
    ): Boolean {
        val maxSide = if (qualityMode == ScanQualityMode.HD) 3200 else 1800
        val jpegQuality = if (qualityMode == ScanQualityMode.HD) 95 else 86
        val interp = if (qualityMode == ScanQualityMode.HD) Imgproc.INTER_CUBIC else Imgproc.INTER_LINEAR

        val srcBitmap = decodeDownsampledBitmap(rawFilePath, maxSide) ?: return false
        val srcMat = Mat()
        val warped = Mat()
        var current = Mat()
        return try {
            Utils.bitmapToMat(srcBitmap, srcMat)
            val w = srcMat.width().toDouble()
            val h = srcMat.height().toDouble()

            val tl = Point(quad.topLeft.x * w, quad.topLeft.y * h)
            val tr = Point(quad.topRight.x * w, quad.topRight.y * h)
            val br = Point(quad.bottomRight.x * w, quad.bottomRight.y * h)
            val bl = Point(quad.bottomLeft.x * w, quad.bottomLeft.y * h)

            val widthTop = hypot(tr.x - tl.x, tr.y - tl.y)
            val widthBottom = hypot(br.x - bl.x, br.y - bl.y)
            val heightLeft = hypot(bl.x - tl.x, bl.y - tl.y)
            val heightRight = hypot(br.x - tr.x, br.y - tr.y)

            val dstW = max(widthTop, widthBottom).roundToInt().coerceIn(360, maxSide)
            val dstH = max(heightLeft, heightRight).roundToInt().coerceIn(360, maxSide)

            val srcPts = MatOfPoint2f(tl, tr, br, bl)
            val dstPts = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((dstW - 1).toDouble(), 0.0),
                Point((dstW - 1).toDouble(), (dstH - 1).toDouble()),
                Point(0.0, (dstH - 1).toDouble())
            )
            val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            try {
                Imgproc.warpPerspective(srcMat, warped, transform, Size(dstW.toDouble(), dstH.toDouble()), interp)
            } finally {
                transform.release()
                srcPts.release()
                dstPts.release()
            }

            current = warped.clone()
            val normalizedRot = ((rotationDegrees % 360) + 360) % 360
            if (normalizedRot != 0) {
                val rotMat = Mat()
                when (normalizedRot) {
                    90 -> Core.rotate(current, rotMat, Core.ROTATE_90_CLOCKWISE)
                    180 -> Core.rotate(current, rotMat, Core.ROTATE_180)
                    270 -> Core.rotate(current, rotMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                }
                current.release()
                current = rotMat
            }

            if (kotlin.math.abs(fineRotationDegrees) > 0.1f) {
                val center = Point(current.width() / 2.0, current.height() / 2.0)
                val rotM = Imgproc.getRotationMatrix2D(center, -fineRotationDegrees.toDouble(), 1.0)
                val fineMat = Mat()
                try {
                    Imgproc.warpAffine(
                        current, fineMat, rotM, current.size(), interp,
                        Core.BORDER_CONSTANT, Scalar(255.0, 255.0, 255.0, 255.0)
                    )
                    current.release()
                    current = fineMat
                } finally {
                    rotM.release()
                }
            }

            if (qualityMode == ScanQualityMode.HD) {
                val rgb = Mat()
                val denoised = Mat()
                try {
                    Imgproc.cvtColor(current, rgb, Imgproc.COLOR_RGBA2RGB)
                    // Edge-preserving bilateral filter cleans sensor grain while keeping ink razor sharp
                    Imgproc.bilateralFilter(rgb, denoised, 5, 28.0, 28.0)
                    val sharp = sharpenMat(denoised, 0.32f)
                    try {
                        Imgproc.cvtColor(sharp, current, Imgproc.COLOR_RGB2RGBA)
                    } finally {
                        sharp.release()
                    }
                } finally {
                    denoised.release()
                    rgb.release()
                }
            }

            saveMatAsJpeg(current, outputFile, jpegQuality)
            true
        } catch (_: Throwable) {
            false
        } finally {
            srcBitmap.recycle()
            srcMat.release()
            warped.release()
            current.release()
        }
    }

    /**
     * Real Book Mode: splits a landscape 2-page spread down the center spine into 2 crisp pages.
     */
    fun splitBookSpread(sourcePath: String, leftOut: File, rightOut: File, qualityMode: ScanQualityMode): Boolean {
        val maxSide = if (qualityMode == ScanQualityMode.HD) 3200 else 1800
        val bmp = decodeDownsampledBitmap(sourcePath, maxSide) ?: return false
        return try {
            val halfW = bmp.width / 2
            if (halfW < 100) return false
            val left = Bitmap.createBitmap(bmp, 0, 0, halfW, bmp.height)
            val right = Bitmap.createBitmap(bmp, halfW, 0, bmp.width - halfW, bmp.height)
            try {
                val q = if (qualityMode == ScanQualityMode.HD) 95 else 88
                saveBitmapAsJpeg(left, leftOut, q)
                saveBitmapAsJpeg(right, rightOut, q)
                true
            } finally {
                left.recycle()
                right.recycle()
            }
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Real ID Card Mode: places Front + Back card scans neatly centered on a crisp white A4 sheet.
     */
    fun combineIdCardFrontBack(frontPath: String, backPath: String, outFile: File): Boolean {
        val front = decodeDownsampledBitmap(frontPath, 1600) ?: return false
        val back = decodeDownsampledBitmap(backPath, 1600) ?: run { front.recycle(); return false }
        val pageW = 1654
        val pageH = 2339
        val canvasBmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(canvasBmp)
            canvas.drawColor(Color.WHITE)
            val targetCardW = (pageW * 0.74f).roundToInt()
            val maxCardH = (pageH * 0.34f).roundToInt()

            fun drawCenteredCard(card: Bitmap, topY: Int) {
                val scale = min(targetCardW.toFloat() / card.width, maxCardH.toFloat() / card.height)
                val dw = (card.width * scale).roundToInt()
                val dh = (card.height * scale).roundToInt()
                val left = (pageW - dw) / 2
                val dst = android.graphics.Rect(left, topY, left + dw, topY + dh)
                canvas.drawBitmap(card, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            }

            drawCenteredCard(front, (pageH * 0.12f).roundToInt())
            drawCenteredCard(back, (pageH * 0.54f).roundToInt())
            saveBitmapAsJpeg(canvasBmp, outFile, 93)
            true
        } finally {
            front.recycle()
            back.recycle()
            canvasBmp.recycle()
        }
    }

    /**
     * Real Cleanup Engine: supports both Surrounding Color (OpenCV Telea Inpainting)
     * and Custom Color (clean anti-aliased fill with chosen swatch color).
     */
    fun applyCleanupStrokes(
        sourceFilePath: String,
        outputFile: File,
        strokes: List<CleanupStroke>,
        qualityMode: ScanQualityMode = ScanQualityMode.FAST
    ): Boolean {
        if (strokes.isEmpty()) return false
        val maxSide = if (qualityMode == ScanQualityMode.HD) 3200 else 2200
        val bmp = decodeDownsampledBitmap(sourceFilePath, maxSide) ?: return false
        val mutable = bmp.copy(Bitmap.Config.ARGB_8888, true)
        bmp.recycle()

        val surroundingStrokes = strokes.filter { it.mode == CleanupBrushMode.SURROUNDING_COLOR }
        val customStrokes = strokes.filter { it.mode == CleanupBrushMode.CUSTOM_COLOR }

        return try {
            val w = mutable.width
            val h = mutable.height
            val baseDim = min(w, h).toFloat()

            // 1. Apply Custom Color strokes directly with smooth round-cap paint
            if (customStrokes.isNotEmpty()) {
                val canvas = Canvas(mutable)
                for (stroke in customStrokes) {
                    if (stroke.points.isEmpty()) continue
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = stroke.customColorArgb
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = (stroke.brushRadiusNorm * 2f * baseDim).coerceAtLeast(8f)
                    }
                    if (stroke.points.size == 1) {
                        val p = stroke.points.first()
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(p.x * w, p.y * h, stroke.brushRadiusNorm * baseDim, paint)
                    } else {
                        val path = Path().apply {
                            moveTo(stroke.points.first().x * w, stroke.points.first().y * h)
                            for (i in 1 until stroke.points.size) {
                                lineTo(stroke.points[i].x * w, stroke.points[i].y * h)
                            }
                        }
                        canvas.drawPath(path, paint)
                    }
                }
            }

            // 2. Apply Surrounding Color strokes via OpenCV Telea Inpainting
            if (surroundingStrokes.isNotEmpty()) {
                val rgba = Mat()
                val bgr = Mat()
                val mask = Mat.zeros(h, w, CvType.CV_8UC1)
                val inpainted = Mat()
                val outRgba = Mat()
                try {
                    Utils.bitmapToMat(mutable, rgba)
                    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)

                    var maxRadiusPx = 9.0
                    for (stroke in surroundingStrokes) {
                        if (stroke.points.isEmpty()) continue
                        val radiusPx = (stroke.brushRadiusNorm * baseDim).roundToInt().coerceIn(5, 140)
                        maxRadiusPx = max(maxRadiusPx, (radiusPx * 0.45).coerceIn(5.0, 18.0))
                        if (stroke.points.size == 1) {
                            val p = stroke.points.first()
                            Imgproc.circle(mask, Point((p.x * w).toDouble(), (p.y * h).toDouble()), radiusPx, Scalar(255.0), -1)
                        } else {
                            for (i in 1 until stroke.points.size) {
                                val p0 = stroke.points[i - 1]
                                val p1 = stroke.points[i]
                                Imgproc.line(
                                    mask,
                                    Point((p0.x * w).toDouble(), (p0.y * h).toDouble()),
                                    Point((p1.x * w).toDouble(), (p1.y * h).toDouble()),
                                    Scalar(255.0),
                                    radiusPx * 2,
                                    Imgproc.LINE_AA
                                )
                            }
                        }
                    }

                    Photo.inpaint(bgr, mask, inpainted, maxRadiusPx, Photo.INPAINT_TELEA)
                    Imgproc.cvtColor(inpainted, outRgba, Imgproc.COLOR_BGR2RGBA)
                    Utils.matToBitmap(outRgba, mutable)
                } finally {
                    outRgba.release()
                    inpainted.release()
                    mask.release()
                    bgr.release()
                    rgba.release()
                }
            }

            saveBitmapAsJpeg(mutable, outputFile, if (qualityMode == ScanQualityMode.HD) 95 else 90)
            true
        } catch (_: Throwable) {
            false
        } finally {
            mutable.recycle()
        }
    }

    /**
     * Real Markup Engine: bakes freehand Pen & translucent Highlighter strokes onto the page.
     */
    fun applyMarkupStrokes(
        sourceFilePath: String,
        outputFile: File,
        strokes: List<MarkupStroke>,
        qualityMode: ScanQualityMode = ScanQualityMode.FAST
    ): Boolean {
        if (strokes.isEmpty()) return false
        val maxSide = if (qualityMode == ScanQualityMode.HD) 3200 else 2200
        val bmp = decodeDownsampledBitmap(sourceFilePath, maxSide) ?: return false
        val mutable = bmp.copy(Bitmap.Config.ARGB_8888, true)
        bmp.recycle()
        return try {
            val canvas = Canvas(mutable)
            val w = mutable.width
            val h = mutable.height
            val baseDim = min(w, h).toFloat()
            for (stroke in strokes) {
                if (stroke.points.size < 2) continue
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.colorArgb
                    alpha = if (stroke.isHighlighter) 92 else 245
                    style = Paint.Style.STROKE
                    strokeCap = if (stroke.isHighlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = (stroke.strokeWidthNorm * baseDim).coerceAtLeast(4f)
                }
                val path = Path().apply {
                    moveTo(stroke.points.first().x * w, stroke.points.first().y * h)
                    for (i in 1 until stroke.points.size) {
                        lineTo(stroke.points[i].x * w, stroke.points[i].y * h)
                    }
                }
                canvas.drawPath(path, paint)
            }
            saveBitmapAsJpeg(mutable, outputFile, if (qualityMode == ScanQualityMode.HD) 95 else 90)
            true
        } finally {
            mutable.recycle()
        }
    }

    /**
     * Non-destructive enhancement from the clean warped source image.
     */
    fun enhanceDocument(
        sourceFilePath: String,
        outputFile: File,
        mode: ScanFilterMode,
        custom: CustomAdjustments = CustomAdjustments(),
        qualityMode: ScanQualityMode = ScanQualityMode.FAST
    ): Boolean {
        val maxSide = if (qualityMode == ScanQualityMode.HD) 3200 else 2000
        val jpegQuality = if (qualityMode == ScanQualityMode.HD) 95 else 88
        val srcBitmap = decodeDownsampledBitmap(sourceFilePath, maxSide) ?: return false
        val rgba = Mat()
        val rgb = Mat()
        var filtered = Mat()
        var blended = Mat()
        var adjusted = Mat()
        val outRgba = Mat()
        return try {
            Utils.bitmapToMat(srcBitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

            filtered = when (mode) {
                ScanFilterMode.ORIGINAL -> rgb.clone()
                ScanFilterMode.AUTO_COLOR -> applyScannerAutoColor(rgb, whitePoint = 216, bleedCutoff = true)
                ScanFilterMode.COLOR -> applyNaturalColor(rgb)
                ScanFilterMode.DOCUMENT -> applyScannerAutoColor(rgb, whitePoint = 204, bleedCutoff = true, strongerInk = true)
                ScanFilterMode.BLACK_AND_WHITE -> applyBlackAndWhite(rgb)
                ScanFilterMode.GRAYSCALE -> applyGrayscale(rgb)
                ScanFilterMode.HIGH_CONTRAST -> applyHighContrast(rgb)
                ScanFilterMode.ENHANCED_DOCUMENT -> applyEnhancedDocument(rgb)
                ScanFilterMode.PHOTO -> applyPhotoMode(rgb)
                ScanFilterMode.SOFT_COLOR -> applySoftColor(rgb)
            }

            val strength = custom.filterStrength.coerceIn(0f, 1f)
            blended = if (mode != ScanFilterMode.ORIGINAL && strength < 0.99f) {
                val mix = Mat()
                Core.addWeighted(filtered, strength.toDouble(), rgb, (1.0 - strength), 0.0, mix)
                mix
            } else {
                filtered.clone()
            }

            adjusted = Mat()
            val alpha = custom.contrast.coerceIn(0.6f, 1.8f).toDouble()
            val beta = (custom.brightness.coerceIn(-0.4f, 0.4f) * 100.0)
            blended.convertTo(adjusted, -1, alpha, beta)

            val sharpAmount = custom.sharpness.coerceIn(0f, 1f)
            if (sharpAmount > 0.02f) {
                val sharp = sharpenMat(adjusted, sharpAmount * 0.75f)
                adjusted.release()
                adjusted = sharp
            }

            Imgproc.cvtColor(adjusted, outRgba, Imgproc.COLOR_RGB2RGBA)
            saveMatAsJpeg(outRgba, outputFile, jpegQuality)
            true
        } catch (_: Throwable) {
            false
        } finally {
            srcBitmap.recycle()
            rgba.release()
            rgb.release()
            filtered.release()
            blended.release()
            adjusted.release()
            outRgba.release()
        }
    }

    /**
     * Adobe Scan-grade 4-stage Auto Color & Document Whitening pipeline:
     * 1. Downsampled morphological dilation + Gaussian blur background illumination estimation & division
     *    (completely eliminates uneven lighting, gray shadows, and yellow paper cast).
     * 2. Smooth white-point LUT tone curve (snaps paper grain & reverse-side ink bleed-through to pure #FFFFFF
     *    while deepening dark ink strokes).
     * 3. Ink-only HSV saturation boost (makes blue/red/green handwriting vivid on pure snow-white paper).
     * 4. Crisp unsharp mask on ink edges.
     */
    private fun applyScannerAutoColor(
        rgb: Mat,
        whitePoint: Int = 216,
        bleedCutoff: Boolean = true,
        strongerInk: Boolean = false
    ): Mat {
        val channels = mutableListOf<Mat>()
        val normalized = mutableListOf<Mat>()
        val merged = Mat()
        val curved = Mat()
        val hsv = Mat()
        val hsvCh = mutableListOf<Mat>()
        val hsvMerged = Mat()
        val boosted = Mat()
        val lut = buildScannerToneLut(whitePoint = whitePoint, bleedCutoff = bleedCutoff, strongerInk = strongerInk)

        // Downscale factor for fast, ultra-smooth background illumination estimation
        val bgScale = min(0.25, 360.0 / max(rgb.width(), rgb.height()).toDouble()).coerceAtLeast(0.08)
        val bgW = max(16, (rgb.width() * bgScale).roundToInt())
        val bgH = max(16, (rgb.height() * bgScale).roundToInt())
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(13.0, 13.0))

        return try {
            Core.split(rgb, channels)
            for (ch in channels) {
                val small = Mat()
                val dilated = Mat()
                val blurred = Mat()
                val bgFull = Mat()
                val div = Mat()
                try {
                    Imgproc.resize(ch, small, Size(bgW.toDouble(), bgH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                    Imgproc.dilate(small, dilated, dilateKernel)
                    Imgproc.GaussianBlur(dilated, blurred, Size(21.0, 21.0), 0.0)
                    Imgproc.resize(blurred, bgFull, ch.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
                    // Prevent division by zero on dark borders
                    Core.max(bgFull, Scalar(18.0), bgFull)
                    Core.divide(ch, bgFull, div, 255.0)
                    normalized.add(div.clone())
                } finally {
                    div.release()
                    bgFull.release()
                    blurred.release()
                    dilated.release()
                    small.release()
                }
            }
            Core.merge(normalized, merged)

            // Apply white-point & bleed-through suppression LUT
            Core.LUT(merged, lut, curved)

            // Boost ink saturation ONLY where pixels are darker than white paper (V < 238)
            Imgproc.cvtColor(curved, hsv, Imgproc.COLOR_RGB2HSV)
            Core.split(hsv, hsvCh)
            val inkMask = Mat()
            val satBoosted = Mat()
            try {
                Imgproc.threshold(hsvCh[2], inkMask, 238.0, 255.0, Imgproc.THRESH_BINARY_INV)
                hsvCh[1].convertTo(satBoosted, -1, if (strongerInk) 1.38 else 1.28, 0.0)
                satBoosted.copyTo(hsvCh[1], inkMask)
                // Clean any residual color cast on pure paper pixels (V >= 242 -> S = 0)
                val paperMask = Mat()
                try {
                    Imgproc.threshold(hsvCh[2], paperMask, 242.0, 255.0, Imgproc.THRESH_BINARY)
                    hsvCh[1].setTo(Scalar(0.0), paperMask)
                } finally {
                    paperMask.release()
                }
            } finally {
                satBoosted.release()
                inkMask.release()
            }
            Core.merge(hsvCh, hsvMerged)
            Imgproc.cvtColor(hsvMerged, boosted, Imgproc.COLOR_HSV2RGB)

            sharpenMat(boosted, if (strongerInk) 0.42f else 0.30f)
        } finally {
            lut.release()
            dilateKernel.release()
            channels.forEach { it.release() }
            normalized.forEach { it.release() }
            merged.release()
            curved.release()
            hsvCh.forEach { it.release() }
            hsvMerged.release()
            hsv.release()
            boosted.release()
        }
    }

    private fun buildScannerToneLut(whitePoint: Int, bleedCutoff: Boolean, strongerInk: Boolean): Mat {
        val lut = Mat(1, 256, CvType.CV_8U)
        val table = ByteArray(256)
        val wp = whitePoint.coerceIn(185, 245).toDouble()
        val bp = if (strongerInk) 28.0 else 16.0
        val gamma = if (strongerInk) 1.32 else 1.18
        for (i in 0..255) {
            val outVal = when {
                bleedCutoff && i >= wp -> 255.0
                i <= bp -> 0.0
                else -> {
                    val norm = ((i - bp) / (wp - bp)).coerceIn(0.0, 1.0)
                    // Smooth S-like ink-deepening curve while keeping bright paper clean
                    val curved = norm.pow(gamma)
                    (curved * 255.0).coerceIn(0.0, 255.0)
                }
            }
            table[i] = outVal.roundToInt().coerceIn(0, 255).toByte()
        }
        lut.put(0, 0, table)
        return lut
    }

    private fun applyNaturalColor(rgb: Mat): Mat {
        val lab = Mat()
        val channels = mutableListOf<Mat>()
        val merged = Mat()
        val out = Mat()
        val clahe = Imgproc.createCLAHE(1.8, Size(8.0, 8.0))
        return try {
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            clahe.apply(channels[0], channels[0])
            Core.merge(channels, merged)
            Imgproc.cvtColor(merged, out, Imgproc.COLOR_Lab2RGB)
            sharpenMat(out, 0.25f)
        } finally {
            channels.forEach { it.release() }
            merged.release()
            lab.release()
            out.release()
        }
    }

    private fun applyBlackAndWhite(rgb: Mat): Mat {
        val clean = applyScannerAutoColor(rgb, whitePoint = 205, bleedCutoff = true, strongerInk = true)
        val gray = Mat()
        val bw = Mat()
        val out = Mat()
        return try {
            Imgproc.cvtColor(clean, gray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.adaptiveThreshold(
                gray, bw, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 31, 14.0
            )
            Imgproc.cvtColor(bw, out, Imgproc.COLOR_GRAY2RGB)
            out
        } finally {
            clean.release()
            gray.release()
            bw.release()
        }
    }

    private fun applyGrayscale(rgb: Mat): Mat {
        val clean = applyScannerAutoColor(rgb, whitePoint = 214, bleedCutoff = true)
        val gray = Mat()
        val out = Mat()
        return try {
            Imgproc.cvtColor(clean, gray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGB)
            out
        } finally {
            clean.release()
            gray.release()
        }
    }

    private fun applyHighContrast(rgb: Mat): Mat {
        val clean = applyScannerAutoColor(rgb, whitePoint = 198, bleedCutoff = true, strongerInk = true)
        val gray = Mat()
        val out = Mat()
        return try {
            Imgproc.cvtColor(clean, gray, Imgproc.COLOR_RGB2GRAY)
            gray.convertTo(gray, -1, 1.38, -28.0)
            Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGB)
            out
        } finally {
            clean.release()
            gray.release()
        }
    }

    private fun applyEnhancedDocument(rgb: Mat): Mat =
        applyScannerAutoColor(rgb, whitePoint = 200, bleedCutoff = true, strongerInk = true)

    private fun applyPhotoMode(rgb: Mat): Mat {
        val out = Mat()
        rgb.convertTo(out, -1, 1.06, 4.0)
        return out
    }

    private fun applySoftColor(rgb: Mat): Mat {
        val clean = applyScannerAutoColor(rgb, whitePoint = 228, bleedCutoff = true, strongerInk = false)
        val blended = Mat()
        return try {
            Core.addWeighted(clean, 0.72, rgb, 0.28, 0.0, blended)
            blended
        } finally {
            clean.release()
        }
    }

    private fun sharpenMat(src: Mat, amount: Float): Mat {
        val blurred = Mat()
        val dst = Mat()
        return try {
            Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), 1.6)
            Core.addWeighted(src, 1.0 + amount, blurred, -amount.toDouble(), 0.0, dst)
            dst
        } finally {
            blurred.release()
        }
    }

    private fun saveMatAsJpeg(rgbaMat: Mat, outFile: File, quality: Int) {
        val outBmp = Bitmap.createBitmap(rgbaMat.width(), rgbaMat.height(), Bitmap.Config.ARGB_8888)
        try {
            Utils.matToBitmap(rgbaMat, outBmp)
            saveBitmapAsJpeg(outBmp, outFile, quality)
        } finally {
            outBmp.recycle()
        }
    }

    private fun saveBitmapAsJpeg(bitmap: Bitmap, outFile: File, quality: Int) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.flush()
        }
    }

    fun decodeDownsampledBitmap(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val maxDim = max(bounds.outWidth, bounds.outHeight)
        if (maxDim <= 0) return null
        var sample = 1
        while (maxDim / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }
}
