package com.offlinepdf.studio.core.imageprocessing

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max

/**
 * Offline neural 4-corner document detector powered by DocAligner (LCNet100, Apache 2.0)
 * running on ONNX Runtime Android (CPU/NNAPI).
 *
 * Input tensor:  [1, 3, 256, 256] float32 RGB in [0.0, 1.0] (NCHW).
 * Output tensor: [1, 4, 128, 128] float32 corner probability heatmaps.
 */
@Singleton
class NeuralDocAlignerDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val inputSize = 256
    private val planeSize = inputSize * inputSize
    private val inputBuffer = FloatArray(3 * planeSize)
    private val rgbByteScratch = ByteArray(3 * planeSize)
    private val lock = Any()

    @Volatile
    private var env: OrtEnvironment? = null

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var inputName: String = "img"

    @Volatile
    private var loadAttempted: Boolean = false

    val isModelReady: Boolean
        get() {
            ensureInitialized()
            return session != null
        }

    private fun ensureInitialized() {
        if (loadAttempted) return
        synchronized(lock) {
            if (loadAttempted) return
            loadAttempted = true
            runCatching {
                val modelFile = extractModelAssetIfPresent() ?: return@runCatching
                val ortEnv = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val ortSession = ortEnv.createSession(modelFile.absolutePath, opts)
                env = ortEnv
                session = ortSession
                inputName = ortSession.inputNames.firstOrNull() ?: "img"
            }
        }
    }

    private fun extractModelAssetIfPresent(): File? {
        val modelDir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(modelDir, "docaligner_lcnet100.onnx")
        if (target.exists() && target.length() > 1_000_000L) return target
        val assetPath = "models/docaligner_lcnet100.onnx"
        return runCatching {
            context.assets.open(assetPath).use { input ->
                val tmp = File(modelDir, "docaligner_lcnet100.onnx.tmp")
                FileOutputStream(tmp).use { output -> input.copyTo(output, 64 * 1024) }
                if (tmp.length() > 1_000_000L) {
                    if (target.exists()) target.delete()
                    tmp.renameTo(target)
                    target
                } else {
                    tmp.delete()
                    null
                }
            }
        }.getOrNull()
    }

    fun detectFromBitmap(bitmap: Bitmap): Pair<DocumentQuad, Float>? {
        ensureInitialized()
        if (session == null) return null
        val rgba = Mat()
        val rgb = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            detectFromRgbMat(rgb)
        } catch (_: Throwable) {
            null
        } finally {
            rgb.release()
            rgba.release()
        }
    }

    fun detectFromRgbMat(rgbMat: Mat): Pair<DocumentQuad, Float>? {
        ensureInitialized()
        val ortEnv = env ?: return null
        val ortSession = session ?: return null
        if (rgbMat.empty()) return null

        synchronized(lock) {
            val resized = Mat()
            return try {
                Imgproc.resize(rgbMat, resized, Size(inputSize.toDouble(), inputSize.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
                resized.get(0, 0, rgbByteScratch)

                // Convert HWC uint8 RGB -> NCHW float32 [0..1]
                var srcIdx = 0
                val gOffset = planeSize
                val bOffset = 2 * planeSize
                val inv255 = 1.0f / 255.0f
                for (i in 0 until planeSize) {
                    inputBuffer[i] = (rgbByteScratch[srcIdx].toInt() and 0xFF) * inv255
                    inputBuffer[gOffset + i] = (rgbByteScratch[srcIdx + 1].toInt() and 0xFF) * inv255
                    inputBuffer[bOffset + i] = (rgbByteScratch[srcIdx + 2].toInt() and 0xFF) * inv255
                    srcIdx += 3
                }

                val shape = longArrayOf(1L, 3L, inputSize.toLong(), inputSize.toLong())
                OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputBuffer), shape).use { tensor ->
                    ortSession.run(Collections.singletonMap(inputName, tensor)).use { result ->
                        val outTensor = result.get(0) as? OnnxTensor ?: return null
                        val outShape = outTensor.info.shape
                        val outBuf = outTensor.floatBuffer ?: return null
                        decodeCornerHeatmaps(outBuf, outShape)
                    }
                }
            } catch (_: Throwable) {
                null
            } finally {
                resized.release()
            }
        }
    }

    private fun decodeCornerHeatmaps(buf: FloatBuffer, shape: LongArray): Pair<DocumentQuad, Float>? {
        if (shape.size != 4 || shape[1] < 4L) return null
        val hmH = shape[2].toInt()
        val hmW = shape[3].toInt()
        if (hmH <= 0 || hmW <= 0) return null
        val hmSize = hmH * hmW

        val pts = ArrayList<NormalizedPoint>(4)
        var minPeak = 1.0f
        var sumPeak = 0.0f

        for (c in 0 until 4) {
            val base = c * hmSize
            var peakVal = -1f
            var peakX = 0
            var peakY = 0
            for (y in 0 until hmH) {
                val rowBase = base + y * hmW
                for (x in 0 until hmW) {
                    val v = buf.get(rowBase + x)
                    if (v > peakVal) {
                        peakVal = v
                        peakX = x
                        peakY = y
                    }
                }
            }
            if (peakVal < 0.28f) return null
            if (peakVal < minPeak) minPeak = peakVal
            sumPeak += peakVal

            // Weighted centroid around peak (radius 8 in 128x128 heatmap) for sub-pixel accuracy
            val threshold = max(0.20f, peakVal * 0.50f)
            val yStart = (peakY - 8).coerceAtLeast(0)
            val yEnd = (peakY + 8).coerceAtMost(hmH - 1)
            val xStart = (peakX - 8).coerceAtLeast(0)
            val xEnd = (peakX + 8).coerceAtMost(hmW - 1)

            var sumW = 0.0f
            var sumX = 0.0f
            var sumY = 0.0f
            for (y in yStart..yEnd) {
                val rowBase = base + y * hmW
                for (x in xStart..xEnd) {
                    val v = buf.get(rowBase + x)
                    if (v >= threshold) {
                        sumW += v
                        sumX += x * v
                        sumY += y * v
                    }
                }
            }
            val cx = if (sumW > 0f) (sumX / sumW + 0.5f) / hmW.toFloat() else (peakX + 0.5f) / hmW.toFloat()
            val cy = if (sumW > 0f) (sumY / sumW + 0.5f) / hmH.toFloat() else (peakY + 0.5f) / hmH.toFloat()
            pts.add(NormalizedPoint(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f)))
        }

        val ordered = orderNormalizedCorners(pts)
        val area = computeQuadArea(ordered)
        if (area < 0.05f || area > 0.99f) return null
        if (!hasValidAngles(ordered)) return null

        val avgConfidence = (sumPeak / 4f).coerceIn(0f, 1f)
        return ordered to avgConfidence
    }

    private fun orderNormalizedCorners(pts: List<NormalizedPoint>): DocumentQuad {
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val sortedByDiff = pts.sortedBy { it.y - it.x }
        return DocumentQuad(
            topLeft = sortedBySum.first(),
            bottomRight = sortedBySum.last(),
            topRight = sortedByDiff.first(),
            bottomLeft = sortedByDiff.last()
        )
    }

    private fun computeQuadArea(q: DocumentQuad): Float {
        val xs = floatArrayOf(q.topLeft.x, q.topRight.x, q.bottomRight.x, q.bottomLeft.x)
        val ys = floatArrayOf(q.topLeft.y, q.topRight.y, q.bottomRight.y, q.bottomLeft.y)
        var sum = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            sum += xs[i] * ys[j] - xs[j] * ys[i]
        }
        return abs(sum) * 0.5f
    }

    private fun hasValidAngles(q: DocumentQuad): Boolean {
        val pts = listOf(q.topLeft, q.topRight, q.bottomRight, q.bottomLeft)
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
            if (len1 < 0.04 || len2 < 0.04) return false
            val cos = ((v1x * v2x + v1y * v2y) / (len1 * len2)).coerceIn(-1.0, 1.0)
            val deg = Math.toDegrees(acos(cos))
            if (deg < 35.0 || deg > 145.0) return false
        }
        return true
    }
}
