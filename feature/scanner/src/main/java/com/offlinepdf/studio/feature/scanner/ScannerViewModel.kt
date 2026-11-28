package com.offlinepdf.studio.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.database.ScanSessionDao
import com.offlinepdf.studio.core.database.ScanSessionPageEntity
import com.offlinepdf.studio.core.imageprocessing.CustomAdjustments
import com.offlinepdf.studio.core.imageprocessing.DocumentEdgeDetector
import com.offlinepdf.studio.core.imageprocessing.DocumentQuad
import com.offlinepdf.studio.core.imageprocessing.ExportFormat
import com.offlinepdf.studio.core.imageprocessing.ExportOptions
import com.offlinepdf.studio.core.imageprocessing.NormalizedPoint
import com.offlinepdf.studio.core.imageprocessing.OpenCvDocumentProcessor
import com.offlinepdf.studio.core.imageprocessing.ScanExportEngine
import com.offlinepdf.studio.core.imageprocessing.ScanExportResult
import com.offlinepdf.studio.core.imageprocessing.ScanFilterMode
import com.offlinepdf.studio.core.imageprocessing.ScanQualityMode
import com.offlinepdf.studio.core.imageprocessing.ScannedPageItem
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

enum class ScannerStage {
    CAMERA,
    CROP,
    ENHANCE,
    PAGES_REVIEW
}

data class ScannerUiState(
    val stage: ScannerStage = ScannerStage.CAMERA,
    val pages: List<ScannedPageItem> = emptyList(),
    val activePageIndex: Int = 0,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val autoCaptureEnabled: Boolean = true,
    val flashEnabled: Boolean = false,
    val qualityMode: ScanQualityMode = ScanQualityMode.FAST,
    val exportOptions: ExportOptions = ExportOptions(
        title = "Scan_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    ),
    val exportProgress: Pair<Int, Int>? = null,
    val lastExportedPdfUri: String? = null,
    val statusMessage: String? = null,
    val previewRevision: Int = 0
) {
    val activePage: ScannedPageItem?
        get() = pages.getOrNull(activePageIndex)
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val edgeDetector: DocumentEdgeDetector,
    private val processor: OpenCvDocumentProcessor,
    private val exportEngine: ScanExportEngine,
    private val storageRepository: SafDocumentRepository,
    private val scanSessionDao: ScanSessionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // Persistent app-private storage (NOT cacheDir) so Android never wipes unsaved scans!
    private val sessionDir: File by lazy {
        File(context.filesDir, "scanner_active_session").apply { mkdirs() }
    }

    /** Serialises DB snapshots so two quick edits can never interleave clearAll()/upsertAll(). */
    private val persistMutex = Mutex()

    /** Serialises image processing so concurrent edits never write the same file at once. */
    private val processingMutex = Mutex()
    private var enhanceJob: Job? = null

    init {
        restoreSavedSession()
    }

    /** Runs [block] as a "busy" operation; the busy overlay is ALWAYS cleared, even on failure. */
    private fun runBusy(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = message) }
            try {
                processingMutex.withLock { block() }
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                _uiState.update { it.copy(statusMessage = t.localizedMessage ?: "Something went wrong. Please try again.") }
            } finally {
                _uiState.update { it.copy(isProcessing = false, processingMessage = "") }
            }
        }
    }

    /** Back navigation inside the scanner flow. Returns true when it consumed the back press. */
    fun navigateBack(): Boolean {
        val state = _uiState.value
        if (state.isProcessing) return true
        return when (state.stage) {
            ScannerStage.ENHANCE -> { _uiState.update { it.copy(stage = ScannerStage.CROP) }; true }
            ScannerStage.CROP -> {
                _uiState.update {
                    it.copy(stage = if (it.pages.isNotEmpty()) ScannerStage.PAGES_REVIEW else ScannerStage.CAMERA)
                }
                true
            }
            ScannerStage.PAGES_REVIEW -> { _uiState.update { it.copy(stage = ScannerStage.CAMERA) }; true }
            ScannerStage.CAMERA -> false
        }
    }

    fun discardAllPages() {
        val pages = _uiState.value.pages
        _uiState.update { it.copy(pages = emptyList(), activePageIndex = 0, stage = ScannerStage.CAMERA) }
        viewModelScope.launch(ioDispatcher) {
            pages.forEach {
                File(it.rawImagePath).delete(); File(it.warpedImagePath).delete(); File(it.enhancedImagePath).delete()
            }
            persistMutex.withLock { runCatching { scanSessionDao.clearAll() } }
        }
    }

    private fun restoreSavedSession() {
        viewModelScope.launch(ioDispatcher) {
            val saved = runCatching { scanSessionDao.getAllOrdered() }.getOrDefault(emptyList())
            val valid = saved.mapNotNull { entity ->
                val rawExists = File(entity.rawImagePath).exists()
                val enhExists = File(entity.enhancedImagePath).exists()
                if (!rawExists || !enhExists) return@mapNotNull null
                val filter = runCatching { ScanFilterMode.valueOf(entity.filterModeName) }
                    .getOrDefault(ScanFilterMode.AUTO_COLOR)
                ScannedPageItem(
                    id = entity.id,
                    rawImagePath = entity.rawImagePath,
                    warpedImagePath = entity.warpedImagePath,
                    enhancedImagePath = entity.enhancedImagePath,
                    quad = DocumentQuad(
                        NormalizedPoint(entity.tlX, entity.tlY),
                        NormalizedPoint(entity.trX, entity.trY),
                        NormalizedPoint(entity.brX, entity.brY),
                        NormalizedPoint(entity.blX, entity.blY)
                    ),
                    rotationDegrees = entity.rotationDegrees,
                    fineRotationDegrees = entity.fineRotationDegrees,
                    filterMode = filter,
                    customAdjustments = CustomAdjustments(
                        entity.brightness, entity.contrast, entity.saturation, entity.sharpness
                    )
                )
            }
            if (valid.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        pages = valid,
                        activePageIndex = valid.lastIndex,
                        statusMessage = "Recovered ${valid.size} unsaved scanned page(s)"
                    )
                }
            }
        }
    }

    private fun persistSessionSnapshot(pages: List<ScannedPageItem>) {
        viewModelScope.launch(ioDispatcher) {
            persistMutex.withLock { runCatching {
                scanSessionDao.clearAll()
                if (pages.isNotEmpty()) {
                    val entities = pages.mapIndexed { idx, p ->
                        ScanSessionPageEntity(
                            id = p.id,
                            orderIndex = idx,
                            rawImagePath = p.rawImagePath,
                            warpedImagePath = p.warpedImagePath,
                            enhancedImagePath = p.enhancedImagePath,
                            tlX = p.quad.topLeft.x, tlY = p.quad.topLeft.y,
                            trX = p.quad.topRight.x, trY = p.quad.topRight.y,
                            brX = p.quad.bottomRight.x, brY = p.quad.bottomRight.y,
                            blX = p.quad.bottomLeft.x, blY = p.quad.bottomLeft.y,
                            rotationDegrees = p.rotationDegrees,
                            fineRotationDegrees = p.fineRotationDegrees,
                            filterModeName = p.filterMode.name,
                            brightness = p.customAdjustments.brightness,
                            contrast = p.customAdjustments.contrast,
                            saturation = p.customAdjustments.saturation,
                            sharpness = p.customAdjustments.sharpness
                        )
                    }
                    scanSessionDao.upsertAll(entities)
                }
            } }
        }
    }

    fun createCaptureTempFile(): File {
        val id = UUID.randomUUID().toString().take(12)
        return File(sessionDir, "${id}_raw.jpg")
    }

    fun toggleAutoCapture() {
        _uiState.update { it.copy(autoCaptureEnabled = !it.autoCaptureEnabled) }
    }

    fun toggleFlash() {
        _uiState.update { it.copy(flashEnabled = !it.flashEnabled) }
    }

    fun toggleQualityMode() {
        _uiState.update {
            val next = if (it.qualityMode == ScanQualityMode.FAST) ScanQualityMode.HD else ScanQualityMode.FAST
            it.copy(
                qualityMode = next,
                statusMessage = if (next == ScanQualityMode.HD) "HD Mode: Maximum sharpness" else "Fast Mode: Quick capture"
            )
        }
    }

    fun openStage(stage: ScannerStage) {
        _uiState.update { it.copy(stage = stage) }
    }

    fun onRawPhotoCaptured(rawFile: File, detectedQuad: DocumentQuad?) {
        val qMode = _uiState.value.qualityMode
        runBusy("Preparing upright scan...") {
            val pageItem = withContext(ioDispatcher) { buildPage(rawFile, detectedQuad, qMode) }
            val updated = _uiState.value.pages + pageItem
            _uiState.update { state ->
                state.copy(
                    pages = updated,
                    activePageIndex = updated.lastIndex,
                    stage = ScannerStage.CROP,
                    previewRevision = state.previewRevision + 1
                )
            }
            persistSessionSnapshot(updated)
        }
    }

    /** Normalises, detects edges, warps and enhances one raw capture. Throws on failure. */
    private suspend fun buildPage(rawFile: File, detectedQuad: DocumentQuad?, qMode: ScanQualityMode): ScannedPageItem {
        check(processor.normalizeExifOrientation(rawFile.absolutePath, maxSide = qMode.maxProcessSide)) {
            "The captured photo could not be read."
        }
        val id = UUID.randomUUID().toString().take(12)
        val quad = detectedQuad ?: run {
            val bmp = processor.decodeDownsampledBitmap(rawFile.absolutePath, 640)
            val found = bmp?.let { edgeDetector.detectDocumentQuad(it, 0) }
            bmp?.recycle()
            found ?: DocumentQuad.fullFrame()
        }
        val warpedFile = File(sessionDir, "${id}_warped.jpg")
        val enhancedFile = File(sessionDir, "${id}_enhanced.jpg")
        check(processor.warpAndRotate(rawFile.absolutePath, warpedFile, quad, 0, 0f, qMode)) {
            "Could not straighten the page."
        }
        check(processor.enhanceDocument(warpedFile.absolutePath, enhancedFile, ScanFilterMode.AUTO_COLOR, qualityMode = qMode)) {
            "Could not enhance the page."
        }
        return ScannedPageItem(
            id = id,
            rawImagePath = rawFile.absolutePath,
            warpedImagePath = warpedFile.absolutePath,
            enhancedImagePath = enhancedFile.absolutePath,
            quad = quad,
            filterMode = ScanFilterMode.AUTO_COLOR
        )
    }

    fun importGalleryImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val qMode = _uiState.value.qualityMode
        runBusy("Importing ${uris.size} image(s)...") {
            val imported = withContext(ioDispatcher) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val rawFile = createCaptureTempFile()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(rawFile).use { output -> input.copyTo(output) }
                        } ?: return@mapNotNull null
                        buildPage(rawFile, null, qMode)
                    }.getOrNull()
                }
            }
            val updated = _uiState.value.pages + imported
            _uiState.update { state ->
                state.copy(
                    pages = updated,
                    activePageIndex = updated.lastIndex.coerceAtLeast(0),
                    stage = if (updated.isNotEmpty()) ScannerStage.PAGES_REVIEW else ScannerStage.CAMERA,
                    statusMessage = if (imported.size < uris.size) "Imported ${imported.size} of ${uris.size} images." else state.statusMessage,
                    previewRevision = state.previewRevision + 1
                )
            }
            persistSessionSnapshot(updated)
        }
    }

    fun updateActiveQuad(quad: DocumentQuad) {
        updateActivePage(persist = false) { it.copy(quad = quad) }
    }

    fun resetActiveQuad() {
        updateActivePage(persist = true) { it.copy(quad = DocumentQuad.fullFrame(0.03f), fineRotationDegrees = 0f) }
    }

    fun autoDetectActiveQuad() {
        val active = _uiState.value.activePage ?: return
        viewModelScope.launch {
            val detected = withContext(ioDispatcher) {
                val bmp = processor.decodeDownsampledBitmap(active.rawImagePath, 640)
                val q = bmp?.let { edgeDetector.detectDocumentQuad(it, 0) }
                bmp?.recycle()
                q ?: DocumentQuad.fullFrame()
            }
            updateActivePage(persist = true) { it.copy(quad = detected) }
        }
    }

    fun rotateActivePage90() {
        val active = _uiState.value.activePage ?: return
        val qMode = _uiState.value.qualityMode
        runBusy("Rotating...") {
            withContext(ioDispatcher) {
                processor.rotateFile90Clockwise(active.rawImagePath, maxSide = qMode.maxProcessSide)
            }
            updateActivePage(persist = true) { it.copy(quad = it.quad.rotated90Clockwise()) }
            _uiState.update { it.copy(previewRevision = it.previewRevision + 1) }
        }
    }

    fun setActiveFineRotation(degrees: Float) {
        updateActivePage(persist = false) { it.copy(fineRotationDegrees = degrees.coerceIn(-15f, 15f)) }
    }

    fun applyCropAndProceedToEnhance() {
        val active = _uiState.value.activePage ?: return
        val qMode = _uiState.value.qualityMode
        runBusy("Correcting perspective...") {
            withContext(ioDispatcher) {
                check(
                    processor.warpAndRotate(
                        rawFilePath = active.rawImagePath,
                        outputFile = File(active.warpedImagePath),
                        quad = active.quad,
                        rotationDegrees = 0,
                        fineRotationDegrees = active.fineRotationDegrees,
                        qualityMode = qMode
                    )
                ) { "Could not straighten the page." }
                check(
                    processor.enhanceDocument(
                        sourceFilePath = active.warpedImagePath,
                        outputFile = File(active.enhancedImagePath),
                        mode = active.filterMode,
                        custom = active.customAdjustments,
                        qualityMode = qMode
                    )
                ) { "Could not enhance the page." }
            }
            persistSessionSnapshot(_uiState.value.pages)
            _uiState.update { it.copy(stage = ScannerStage.ENHANCE, previewRevision = it.previewRevision + 1) }
        }
    }

    fun setActiveFilter(mode: ScanFilterMode) {
        val active = _uiState.value.activePage ?: return
        val updated = active.copy(filterMode = mode)
        updateActivePage(persist = true) { updated }
        reprocessEnhanced(updated, debounceMillis = 0L)
    }

    /** Slider changes: state updates instantly, the (heavy) image work is debounced, latest wins. */
    fun setActiveCustomAdjustments(custom: CustomAdjustments, final: Boolean = false) {
        val active = _uiState.value.activePage ?: return
        val updated = active.copy(filterMode = ScanFilterMode.CUSTOM, customAdjustments = custom)
        updateActivePage(persist = final) { updated }
        reprocessEnhanced(updated, debounceMillis = if (final) 0L else 220L)
    }

    private fun reprocessEnhanced(item: ScannedPageItem, debounceMillis: Long) {
        val qMode = _uiState.value.qualityMode
        enhanceJob?.cancel()
        enhanceJob = viewModelScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Enhancing...") }
            try {
                processingMutex.withLock {
                    withContext(ioDispatcher) {
                        processor.enhanceDocument(
                            sourceFilePath = item.warpedImagePath,
                            outputFile = File(item.enhancedImagePath),
                            mode = item.filterMode,
                            custom = item.customAdjustments,
                            qualityMode = qMode
                        )
                    }
                }
                _uiState.update { it.copy(previewRevision = it.previewRevision + 1) }
                persistSessionSnapshot(_uiState.value.pages)
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                _uiState.update { it.copy(statusMessage = t.localizedMessage ?: "Enhancement failed.") }
            } finally {
                _uiState.update { it.copy(isProcessing = false, processingMessage = "") }
            }
        }
    }

    fun applyActiveFilterToAllPages() {
        val active = _uiState.value.activePage ?: return
        val mode = active.filterMode
        val custom = active.customAdjustments
        val allPages = _uiState.value.pages
        val qMode = _uiState.value.qualityMode
        if (allPages.isEmpty()) return

        runBusy("Applying filter to ${allPages.size} pages...") {
            val updatedPages = withContext(ioDispatcher) {
                allPages.map { p ->
                    val next = p.copy(filterMode = mode, customAdjustments = custom)
                    processor.enhanceDocument(next.warpedImagePath, File(next.enhancedImagePath), mode, custom, qMode)
                    next
                }
            }
            _uiState.update {
                it.copy(
                    pages = updatedPages,
                    statusMessage = "Applied ${mode.displayName} to all ${updatedPages.size} pages",
                    previewRevision = it.previewRevision + 1
                )
            }
            persistSessionSnapshot(updatedPages)
        }
    }

    fun selectPage(index: Int, targetStage: ScannerStage) {
        if (index in _uiState.value.pages.indices) {
            _uiState.update { it.copy(activePageIndex = index, stage = targetStage) }
        }
    }

    fun retakeActivePage() {
        val idx = _uiState.value.activePageIndex
        deletePage(idx)
        _uiState.update { it.copy(stage = ScannerStage.CAMERA) }
    }

    fun duplicatePage(index: Int) {
        val source = _uiState.value.pages.getOrNull(index) ?: return
        viewModelScope.launch {
            val copyItem = withContext(ioDispatcher) {
                val id = UUID.randomUUID().toString().take(12)
                val rawCopy = File(sessionDir, "${id}_raw.jpg")
                val warpedCopy = File(sessionDir, "${id}_warped.jpg")
                val enhCopy = File(sessionDir, "${id}_enhanced.jpg")
                File(source.rawImagePath).copyTo(rawCopy, overwrite = true)
                File(source.warpedImagePath).copyTo(warpedCopy, overwrite = true)
                File(source.enhancedImagePath).copyTo(enhCopy, overwrite = true)
                source.copy(
                    id = id,
                    rawImagePath = rawCopy.absolutePath,
                    warpedImagePath = warpedCopy.absolutePath,
                    enhancedImagePath = enhCopy.absolutePath
                )
            }
            val list = _uiState.value.pages.toMutableList()
            list.add((index + 1).coerceAtMost(list.size), copyItem)
            _uiState.update { state ->
                state.copy(pages = list, previewRevision = state.previewRevision + 1)
            }
            persistSessionSnapshot(list)
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val cur = _uiState.value.pages
        if (fromIndex !in cur.indices || toIndex !in cur.indices || fromIndex == toIndex) return
        val list = cur.toMutableList()
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        _uiState.update { it.copy(pages = list, activePageIndex = toIndex) }
        persistSessionSnapshot(list)
    }

    fun rotatePageInQueue(index: Int) {
        val page = _uiState.value.pages.getOrNull(index) ?: return
        val qMode = _uiState.value.qualityMode
        runBusy("Rotating page...") {
            withContext(ioDispatcher) {
                processor.rotateFile90Clockwise(page.rawImagePath, maxSide = qMode.maxProcessSide)
                processor.rotateFile90Clockwise(page.warpedImagePath, maxSide = qMode.maxProcessSide)
                processor.rotateFile90Clockwise(page.enhancedImagePath, maxSide = qMode.maxProcessSide)
            }
            val list = _uiState.value.pages.toMutableList()
            if (index in list.indices) list[index] = list[index].copy(quad = list[index].quad.rotated90Clockwise())
            _uiState.update { it.copy(pages = list, previewRevision = it.previewRevision + 1) }
            persistSessionSnapshot(list)
        }
    }

    fun deletePage(index: Int) {
        val removed = _uiState.value.pages.getOrNull(index) ?: return
        viewModelScope.launch(ioDispatcher) {
            File(removed.rawImagePath).delete()
            File(removed.warpedImagePath).delete()
            File(removed.enhancedImagePath).delete()
        }
        val list = _uiState.value.pages.toMutableList().apply { removeAt(index) }
        _uiState.update { state ->
            val nextIdx = state.activePageIndex.coerceAtMost((list.size - 1).coerceAtLeast(0))
            val nextStage = if (list.isEmpty()) ScannerStage.CAMERA else state.stage
            state.copy(pages = list, activePageIndex = nextIdx, stage = nextStage)
        }
        persistSessionSnapshot(list)
    }

    fun updateExportOptions(options: ExportOptions) {
        _uiState.update { it.copy(exportOptions = options) }
    }

    fun exportToSafUri(destinationUri: Uri) {
        val pages = _uiState.value.pages
        val options = _uiState.value.exportOptions
        if (pages.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Exporting...", exportProgress = 0 to pages.size) }
            val result = exportEngine.exportPages(
                pages = pages,
                options = options,
                destinationUri = destinationUri,
                onProgress = { cur, total ->
                    _uiState.update { it.copy(exportProgress = cur to total) }
                }
            )

            when (result) {
                is ScanExportResult.Success -> {
                    var exportedPdfUri: String? = null
                    if (options.format == ExportFormat.PDF) {
                        val fileName = "${options.title.ifBlank { "Scanned_Document" }}.pdf"
                        runCatching { storageRepository.registerScannedPdf(destinationUri, fileName, pages.size) }
                        exportedPdfUri = destinationUri.toString()
                    }
                    // Only clear session after ALL pages are verified exported!
                    withContext(ioDispatcher) {
                        scanSessionDao.clearAll()
                        sessionDir.listFiles()?.forEach { it.delete() }
                    }
                    val msg = if (result.isZipArchive) {
                        "Exported all ${result.exportedPages} images as ZIP!"
                    } else {
                        "Exported ${result.exportedPages} page(s) successfully!"
                    }
                    _uiState.update {
                        it.copy(
                            pages = emptyList(),
                            activePageIndex = 0,
                            stage = ScannerStage.CAMERA,
                            isProcessing = false,
                            processingMessage = "",
                            exportProgress = null,
                            lastExportedPdfUri = exportedPdfUri,
                            statusMessage = msg
                        )
                    }
                }
                is ScanExportResult.Failure -> {
                    // Preserve 100% of originals on any failure!
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            processingMessage = "",
                            exportProgress = null,
                            statusMessage = result.reason
                        )
                    }
                }
            }
        }
    }

    suspend fun loadPreviewBitmap(path: String, maxSide: Int = 1280): Bitmap? =
        withContext(ioDispatcher) { processor.decodeDownsampledBitmap(path, maxSide) }

    suspend fun prepareShareUri(uriString: String): Uri =
        storageRepository.prepareShareUri(uriString, _uiState.value.exportOptions.title)

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearLastExportedPdfUri() {
        _uiState.update { it.copy(lastExportedPdfUri = null) }
    }

    private fun updateActivePage(persist: Boolean, transform: (ScannedPageItem) -> ScannedPageItem) {
        val state = _uiState.value
        val idx = state.activePageIndex
        if (idx !in state.pages.indices) return
        val list = state.pages.toMutableList()
        list[idx] = transform(list[idx])
        _uiState.update { it.copy(pages = list) }
        if (persist) persistSessionSnapshot(list)
    }
}
