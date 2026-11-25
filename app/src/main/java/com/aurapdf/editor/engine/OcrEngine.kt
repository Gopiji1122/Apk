package com.aurapdf.editor.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrEngine {

    private suspend fun recognizeBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    recognizer.close()
                    if (cont.isActive) cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    recognizer.close()
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }

    /** Extract text offline from either a single page or the entire PDF */
    suspend fun extractTextFromPdf(
        pdfFile: File,
        singlePageIndex: Int? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return@withContext ""
                val indices = if (singlePageIndex != null) {
                    listOf(singlePageIndex.coerceIn(0, renderer.pageCount - 1))
                } else {
                    (0 until renderer.pageCount).toList()
                }
                indices.forEachIndexed { step, pageIdx ->
                    onProgress(step + 1, indices.size)
                    val bmp = renderer.openPage(pageIdx).use { page ->
                        val targetW = 1440
                        val targetH = (targetW.toFloat() / page.width.coerceAtLeast(1) * page.height)
                            .toInt()
                            .coerceAtLeast(1)
                        Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                    try {
                        val pageText = recognizeBitmap(bmp)
                        if (indices.size > 1) {
                            sb.append("--- Page ${pageIdx + 1} ---\n")
                        }
                        sb.append(pageText.trim()).append("\n\n")
                    } finally {
                        bmp.recycle()
                    }
                }
            }
        }
        sb.toString().trim()
    }
}
