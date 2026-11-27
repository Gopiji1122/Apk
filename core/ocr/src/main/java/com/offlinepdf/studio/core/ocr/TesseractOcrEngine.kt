package com.offlinepdf.studio.core.ocr

import android.content.Context
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import com.offlinepdf.studio.core.common.DefaultDispatcher
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.imageprocessing.OpenCvDocumentProcessor
import com.offlinepdf.studio.core.imageprocessing.PositionedOcrWord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class OcrLanguageStatus(
    val engAvailable: Boolean,
    val hinAvailable: Boolean
) {
    fun supports(langCode: String): Boolean = when (langCode) {
        "eng" -> engAvailable
        "hin" -> hinAvailable
        "eng+hin" -> engAvailable && hinAvailable
        else -> engAvailable
    }
}

data class PageOcrResult(
    val pageIndex: Int,
    val fullText: String,
    val words: List<PositionedOcrWord>
)

sealed interface OcrRunResult {
    data class Success(val pages: List<PageOcrResult>) : OcrRunResult
    data class MissingLanguageData(val missingCodes: List<String>) : OcrRunResult
    data class Error(val message: String) : OcrRunResult
}

@Singleton
class TesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: OpenCvDocumentProcessor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    private val tessRootDir: File by lazy { File(context.filesDir, "tesseract").apply { mkdirs() } }
    private val tessDataDir: File by lazy { File(tessRootDir, "tessdata").apply { mkdirs() } }

    @Volatile
    private var activeTessApi: TessBaseAPI? = null

    /**
     * Checks whether local traineddata files exist in app-private storage, extracting any
     * bundled APK assets/tessdata/*.traineddata first. Never downloads from the internet.
     */
    suspend fun checkLanguageAvailability(): OcrLanguageStatus = withContext(ioDispatcher) {
        extractBundledAssetsIfPresent()
        OcrLanguageStatus(
            engAvailable = File(tessDataDir, "eng.traineddata").let { it.exists() && it.length() > 100_000L },
            hinAvailable = File(tessDataDir, "hin.traineddata").let { it.exists() && it.length() > 100_000L }
        )
    }

    private fun extractBundledAssetsIfPresent() {
        val assetFiles = runCatching { context.assets.list("tessdata") }.getOrNull() ?: return
        for (name in assetFiles) {
            if (!name.endsWith(".traineddata")) continue
            val dest = File(tessDataDir, name)
            if (dest.exists() && dest.length() > 100_000L) continue
            runCatching {
                context.assets.open("tessdata/$name").use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) }
                }
            }
        }
    }

    /**
     * Imports a user-provided .traineddata file (e.g. eng.traineddata or hin.traineddata)
     * from local storage via SAF so OCR works 100% offline.
     */
    suspend fun importTrainedDataFile(uri: Uri, targetLangCode: String): Boolean = withContext(ioDispatcher) {
        val safeCode = if (targetLangCode == "hin") "hin" else "eng"
        val dest = File(tessDataDir, "$safeCode.traineddata")
        val tmp = File(tessDataDir, "$safeCode.traineddata.tmp")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return@runCatching false
            if (tmp.length() < 100_000L) {
                tmp.delete()
                return@runCatching false
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        }.getOrDefault(false)
    }

    fun cancelOngoingOcr() {
        runCatching { activeTessApi?.stop() }
    }

    /**
     * Runs real offline multi-page Tesseract OCR with progress reporting, cancellation support,
     * and word bounding-box extraction for Searchable PDF generation.
     */
    suspend fun recognizePages(
        imagePaths: List<String>,
        languageCode: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): OcrRunResult = withContext(defaultDispatcher) {
        val status = checkLanguageAvailability()
        val requiredCodes = languageCode.split("+").filter { it.isNotBlank() }
        val missing = requiredCodes.filter { !status.supports(it) }
        if (missing.isNotEmpty()) {
            return@withContext OcrRunResult.MissingLanguageData(missing)
        }

        val tess = TessBaseAPI()
        activeTessApi = tess
        try {
            val initialized = tess.init(tessRootDir.absolutePath, languageCode)
            if (!initialized) {
                return@withContext OcrRunResult.Error("Failed to initialize Tesseract for '$languageCode'.")
            }

            val results = mutableListOf<PageOcrResult>()
            imagePaths.forEachIndexed { idx, path ->
                coroutineContext.ensureActive()
                onProgress(idx + 1, imagePaths.size)
                val bmp = processor.decodeDownsampledBitmap(path, maxSide = 2000)
                    ?: return@withContext OcrRunResult.Error("Could not decode page #${idx + 1} for OCR.")
                try {
                    tess.setImage(bmp)
                    val text = tess.getUTF8Text().orEmpty().trim()
                    val words = extractWordBoxes(tess, bmp.width.toFloat(), bmp.height.toFloat())
                    results.add(PageOcrResult(pageIndex = idx, fullText = text, words = words))
                } finally {
                    bmp.recycle()
                }
            }
            OcrRunResult.Success(results)
        } catch (t: Throwable) {
            OcrRunResult.Error(t.localizedMessage ?: "OCR cancelled or failed.")
        } finally {
            activeTessApi = null
            runCatching { tess.recycle() }
        }
    }

    private fun extractWordBoxes(tess: TessBaseAPI, imgW: Float, imgH: Float): List<PositionedOcrWord> {
        if (imgW <= 1f || imgH <= 1f) return emptyList()
        val out = mutableListOf<PositionedOcrWord>()
        val iter = runCatching { tess.resultIterator }.getOrNull() ?: return emptyList()
        try {
            iter.begin()
            do {
                val word = iter.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim()
                val rect = iter.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                if (!word.isNullOrBlank() && rect != null) {
                    out.add(
                        PositionedOcrWord(
                            text = word,
                            leftNorm = (rect.left / imgW).coerceIn(0f, 1f),
                            topNorm = (rect.top / imgH).coerceIn(0f, 1f),
                            rightNorm = (rect.right / imgW).coerceIn(0f, 1f),
                            bottomNorm = (rect.bottom / imgH).coerceIn(0f, 1f)
                        )
                    )
                }
            } while (iter.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        } catch (_: Throwable) {
            // Fall back cleanly if iterator fails on a blank page
        } finally {
            runCatching { iter.delete() }
        }
        return out
    }
}
