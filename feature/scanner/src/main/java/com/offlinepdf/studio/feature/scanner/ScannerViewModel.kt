package com.offlinepdf.studio.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaActionSound
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.database.DocumentEntity
import com.offlinepdf.studio.core.database.ScanSessionDao
import com.offlinepdf.studio.core.database.ScanSessionPageEntity
import com.offlinepdf.studio.core.database.UserPreferences
import com.offlinepdf.studio.core.database.UserPreferencesRepository
import com.offlinepdf.studio.core.imageprocessing.CustomAdjustments
import com.offlinepdf.studio.core.imageprocessing.DocumentEdgeDetector
import com.offlinepdf.studio.core.imageprocessing.DocumentQuad
import com.offlinepdf.studio.core.imageprocessing.ExportFormat
import com.offlinepdf.studio.core.imageprocessing.ExportOptions
import com.offlinepdf.studio.core.imageprocessing.NormalizedPoint
import com.offlinepdf.studio.core.imageprocessing.OpenCvDocumentProcessor
import com.offlinepdf.studio.core.imageprocessing.PositionedOcrWord
import com.offlinepdf.studio.core.imageprocessing.ScanExportEngine
import com.offlinepdf.studio.core.imageprocessing.ScanExportResult
import com.offlinepdf.studio.core.imageprocessing.ScanFilterMode
import com.offlinepdf.studio.core.imageprocessing.ScanQualityMode
import com.offlinepdf.studio.core.imageprocessing.ScannedPageItem
import com.offlinepdf.studio.core.ocr.OcrLanguageStatus
import com.offlinepdf.studio.core.ocr.OcrRunResult
import com.offlinepdf.studio.core.ocr.PageOcrResult
import com.offlinepdf.studio.core.ocr.TesseractOcrEngine
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

enum class ScannerStage {
    HUB,
    CAMERA,
    CROP,
    ENHANCE,
    PAGES_REVIEW
}

data class PageEditSnapshot(
    val filterMode: ScanFilterMode,
    val adjustments: CustomAdjustments
)

data class ScannerUiState(
    val stage: ScannerStage = ScannerStage.HUB,
    val pages: List<ScannedPageItem> = emptyList(),
    val recentScans: List<DocumentEntity> = emptyList(),
    val activePageIndex: Int = 0,
    val replacingPageIndex: Int? = null,
    val recoveredSessionCount: Int = 0,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val autoCaptureEnabled: Boolean = true,
    val captureSoundEnabled: Boolean = true,
    val batchModeEnabled: Boolean = false,
    val flashEnabled: Boolean = false,
    val detectionSensitivity: String = "NORMAL",
    val qualityMode: ScanQualityMode = ScanQualityMode.FAST,
    val exportOptions: ExportOptions = ExportOptions(
        title = "Scan_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    ),
    val exportProgress: Pair<Int, Int>? = null,
    val lastExportedPdfUri: String? = null,
    // Undo / Redo for current page editor
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    // Offline OCR state
    val ocrLangStatus: OcrLanguageStatus = OcrLanguageStatus(false, false),
    val ocrLanguageCode: String = "eng+hin",
    val isRunningOcr: Boolean = false,
    val ocrProgress: Pair<Int, Int>? = null,
    val ocrResults: List<PageOcrResult> = emptyList(),
    val ocrSearchQuery: String = "",
    val statusMessage: String? = null,
    val previewRevision: Int = 0
) {
    val activePage: ScannedPageItem?
        get() = pages.getOrNull(activePageIndex)

    val combinedOcrText: String
        get() = ocrResults.joinToString("\n\n--- Page Break ---\n\n") {
            "Page ${it.pageIndex + 1}:\n${it.fullText}"
        }
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val edgeDetector: DocumentEdgeDetector,
    private val processor: OpenCvDocumentProcessor,
    private val exportEngine: ScanExportEngine,
    private val ocrEngine: TesseractOcrEngine,
    private val storageRepository: SafDocumentRepository,
    private val scanSessionDao: ScanSessionDao,
    private val prefsRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val sessionDir: File by lazy {
        File(context.filesDir, "scanner_active_session").apply { mkdirs() }
    }

    private val shutterSound: MediaActionSound by lazy {
        MediaActionSound().apply { runCatching { load(MediaActionSound.SHUTTER_CLICK) } }
    }

    private val undoStack = ArrayDeque<PageEditSnapshot>()
    private val redoStack = ArrayDeque<PageEditSnapshot>()
    private var ocrJob: Job? = null

    init {
        observePreferences()
        observeRecentScans()
        restoreSavedSession()
        refreshOcrStatus()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            prefsRepository.preferences.collect { prefs: UserPreferences ->
                val defaultFmt = runCatching { ExportFormat.valueOf(prefs.scannerDefaultExportFormat) }
                    .getOrDefault(ExportFormat.PDF)
                _uiState.update { state ->
                    state.copy(
                        autoCaptureEnabled = prefs.scannerAutoCapture,
                        captureSoundEnabled = prefs.scannerCaptureSound,
                        batchModeEnabled = prefs.scannerBatchMode,
                        flashEnabled = prefs.scannerDefaultFlash,
                        detectionSensitivity = prefs.scannerDetectionSensitivity,
                        ocrLanguageCode = prefs.scannerOcrLanguage,
                        exportOptions = state.exportOptions.copy(
                            format = defaultFmt,
                            jpegQuality = prefs.scannerDefaultPdfQuality
                        )
                    )
                }
            }
        }
    }

    private fun observeRecentScans() {
        viewModelScope.launch {
            storageRepository.observeScannedDocuments().collect { docs ->
                _uiState.update { it.copy(recentScans = docs) }
            }
        }
    }

    fun refreshOcrStatus() {
        viewModelScope.launch {
            val status = ocrEngine.checkLanguageAvailability()
            _uiState.update { it.copy(ocrLangStatus = status) }
        }
    }

    private fun restoreSavedSession() {
        viewModelScope.launch(ioDispatcher) {
            val saved = runCatching { scanSessionDao.getAllOrdered() }.getOrDefault(emptyList())
            val valid = saved.mapNotNull { entity ->
                if (!File(entity.rawImagePath).exists() || !File(entity.enhancedImagePath).exists()) {
                    return@mapNotNull null
                }
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
                        brightness = entity.brightness,
                        contrast = entity.contrast,
                        sharpness = entity.sharpness,
                        filterStrength = entity.saturation.coerceIn(0f, 1f)
                    )
                )
            }
            if (valid.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        pages = valid,
                        activePageIndex = valid.lastIndex,
                        recoveredSessionCount = valid.size
                    )
                }
            }
        }
    }

    fun discardRecoveredSession() {
        viewModelScope.launch(ioDispatcher) {
            scanSessionDao.clearAll()
            sessionDir.listFiles()?.forEach { it.delete() }
        }
        _uiState.update {
            it.copy(
                pages = emptyList(),
                activePageIndex = 0,
                recoveredSessionCount = 0,
                ocrResults = emptyList(),
                statusMessage = "Discarded unsaved scan session."
            )
        }
    }

    fun resumeRecoveredSession() {
        _uiState.update { it.copy(recoveredSessionCount = 0, stage = ScannerStage.PAGES_REVIEW) }
    }

    private fun persistSessionSnapshot(pages: List<ScannedPageItem>) {
        viewModelScope.launch(ioDispatcher) {
            runCatching {
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
                            saturation = p.customAdjustments.filterStrength,
                            sharpness = p.customAdjustments.sharpness
                        )
                    }
                    scanSessionDao.upsertAll(entities)
                }
            }
        }
    }

    fun createCaptureTempFile(): File {
        val id = UUID.randomUUID().toString().take(12)
        return File(sessionDir, "${id}_raw.jpg")
    }

    fun toggleAutoCapture() {
        val next = !_uiState.value.autoCaptureEnabled
        _uiState.update { it.copy(autoCaptureEnabled = next) }
        viewModelScope.launch { prefsRepository.setScannerAutoCapture(next) }
    }

    fun toggleCaptureSound() {
        val next = !_uiState.value.captureSoundEnabled
        _uiState.update { it.copy(captureSoundEnabled = next) }
        viewModelScope.launch { prefsRepository.setScannerCaptureSound(next) }
    }

    fun toggleBatchMode() {
        val next = !_uiState.value.batchModeEnabled
        _uiState.update {
            it.copy(
                batchModeEnabled = next,
                statusMessage = if (next) "Batch Mode: Continuous multi-page capture" else "Single Review Mode"
            )
        }
        viewModelScope.launch { prefsRepository.setScannerBatchMode(next) }
    }

    fun toggleFlash() {
        val next = !_uiState.value.flashEnabled
        _uiState.update { it.copy(flashEnabled = next) }
        viewModelScope.launch { prefsRepository.setScannerDefaultFlash(next) }
    }

    fun setDetectionSensitivity(level: String) {
        _uiState.update { it.copy(detectionSensitivity = level) }
        viewModelScope.launch { prefsRepository.setScannerDetectionSensitivity(level) }
    }

    fun setOcrLanguage(lang: String) {
        _uiState.update { it.copy(ocrLanguageCode = lang) }
        viewModelScope.launch { prefsRepository.setScannerOcrLanguage(lang) }
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
        _uiState.update { it.copy(stage = stage, replacingPageIndex = if (stage != ScannerStage.CAMERA) null else it.replacingPageIndex) }
    }

    fun startReplacePage(index: Int) {
        _uiState.update { it.copy(replacingPageIndex = index, stage = ScannerStage.CAMERA) }
    }

    fun onRawPhotoCaptured(rawFile: File, detectedQuad: DocumentQuad?) {
        if (_uiState.value.captureSoundEnabled) {
            runCatching { shutterSound.play(MediaActionSound.SHUTTER_CLICK) }
        }
        val qMode = _uiState.value.qualityMode
        val replaceIdx = _uiState.value.replacingPageIndex
        val isBatch = _uiState.value.batchModeEnabled && replaceIdx == null

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Processing scan...") }
            val pageItem = withContext(ioDispatcher) {
                processor.normalizeExifOrientation(rawFile.absolutePath, maxSide = qMode.maxProcessSide)
                val id = UUID.randomUUID().toString().take(12)
                val quad = detectedQuad ?: run {
                    val bmp = processor.decodeDownsampledBitmap(rawFile.absolutePath, 640)
                    val found = bmp?.let { edgeDetector.detectDocumentQuad(it, 0) }
                    bmp?.recycle()
                    found ?: DocumentQuad.fullFrame()
                }
                val warpedFile = File(sessionDir, "${id}_warped.jpg")
                val enhancedFile = File(sessionDir, "${id}_enhanced.jpg")

                processor.warpAndRotate(rawFile.absolutePath, warpedFile, quad, 0, 0f, qMode)
                processor.enhanceDocument(warpedFile.absolutePath, enhancedFile, ScanFilterMode.AUTO_COLOR, qualityMode = qMode)

                ScannedPageItem(
                    id = id,
                    rawImagePath = rawFile.absolutePath,
                    warpedImagePath = warpedFile.absolutePath,
                    enhancedImagePath = enhancedFile.absolutePath,
                    quad = quad,
                    filterMode = ScanFilterMode.AUTO_COLOR
                )
            }

            val currentList = _uiState.value.pages.toMutableList()
            val targetIdx = if (replaceIdx != null && replaceIdx in currentList.indices) {
                val old = currentList[replaceIdx]
                viewModelScope.launch(ioDispatcher) {
                    File(old.rawImagePath).delete()
                    File(old.warpedImagePath).delete()
                    File(old.enhancedImagePath).delete()
                }
                currentList[replaceIdx] = pageItem
                replaceIdx
            } else {
                currentList.add(pageItem)
                currentList.lastIndex
            }

            clearUndoRedo()
            _uiState.update { state ->
                state.copy(
                    pages = currentList,
                    activePageIndex = targetIdx,
                    replacingPageIndex = null,
                    recoveredSessionCount = 0,
                    stage = if (isBatch) ScannerStage.CAMERA else ScannerStage.CROP,
                    isProcessing = false,
                    processingMessage = "",
                    statusMessage = if (isBatch) "Page #${currentList.size} captured" else null,
                    previewRevision = state.previewRevision + 1
                )
            }
            persistSessionSnapshot(currentList)
        }
    }

    fun importGalleryImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val qMode = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Importing ${uris.size} image(s)...") }
            val imported = withContext(ioDispatcher) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val rawFile = createCaptureTempFile()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(rawFile).use { output -> input.copyTo(output) }
                        } ?: return@mapNotNull null

                        processor.normalizeExifOrientation(rawFile.absolutePath, maxSide = qMode.maxProcessSide)
                        val bmp = processor.decodeDownsampledBitmap(rawFile.absolutePath, 640)
                        val quad = bmp?.let { edgeDetector.detectDocumentQuad(it, 0) } ?: DocumentQuad.fullFrame()
                        bmp?.recycle()

                        val id = UUID.randomUUID().toString().take(12)
                        val warpedFile = File(sessionDir, "${id}_warped.jpg")
                        val enhancedFile = File(sessionDir, "${id}_enhanced.jpg")
                        processor.warpAndRotate(rawFile.absolutePath, warpedFile, quad, 0, 0f, qMode)
                        processor.enhanceDocument(warpedFile.absolutePath, enhancedFile, ScanFilterMode.AUTO_COLOR, qualityMode = qMode)

                        ScannedPageItem(
                            id = id,
                            rawImagePath = rawFile.absolutePath,
                            warpedImagePath = warpedFile.absolutePath,
                            enhancedImagePath = enhancedFile.absolutePath,
                            quad = quad,
                            filterMode = ScanFilterMode.AUTO_COLOR
                        )
                    }.getOrNull()
                }
            }

            val updated = _uiState.value.pages + imported
            _uiState.update { state ->
                state.copy(
                    pages = updated,
                    activePageIndex = updated.lastIndex.coerceAtLeast(0),
                    recoveredSessionCount = 0,
                    stage = if (updated.isNotEmpty()) ScannerStage.PAGES_REVIEW else ScannerStage.HUB,
                    isProcessing = false,
                    processingMessage = "",
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

    fun rotateActivePage90(clockwise: Boolean = true) {
        val active = _uiState.value.activePage ?: return
        val qMode = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Rotating...") }
            withContext(ioDispatcher) {
                processor.rotateFile90(active.rawImagePath, clockwise = clockwise, maxSide = qMode.maxProcessSide)
            }
            updateActivePage(persist = true) {
                val nextQuad = if (clockwise) it.quad.rotated90Clockwise() else it.quad.rotated90CounterClockwise()
                it.copy(quad = nextQuad)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingMessage = "",
                    previewRevision = it.previewRevision + 1
                )
            }
        }
    }

    fun setActiveFineRotation(degrees: Float) {
        updateActivePage(persist = false) { it.copy(fineRotationDegrees = degrees.coerceIn(-15f, 15f)) }
    }

    fun applyCropAndProceedToEnhance() {
        val active = _uiState.value.activePage ?: return
        val qMode = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Correcting perspective...") }
            withContext(ioDispatcher) {
                processor.warpAndRotate(
                    rawFilePath = active.rawImagePath,
                    outputFile = File(active.warpedImagePath),
                    quad = active.quad,
                    rotationDegrees = 0,
                    fineRotationDegrees = active.fineRotationDegrees,
                    qualityMode = qMode
                )
                processor.enhanceDocument(
                    sourceFilePath = active.warpedImagePath,
                    outputFile = File(active.enhancedImagePath),
                    mode = active.filterMode,
                    custom = active.customAdjustments,
                    qualityMode = qMode
                )
            }
            persistSessionSnapshot(_uiState.value.pages)
            clearUndoRedo()
            _uiState.update {
                it.copy(
                    stage = ScannerStage.ENHANCE,
                    isProcessing = false,
                    processingMessage = "",
                    previewRevision = it.previewRevision + 1
                )
            }
        }
    }

    private fun pushUndoSnapshot(page: ScannedPageItem) {
        undoStack.addLast(PageEditSnapshot(page.filterMode, page.customAdjustments))
        if (undoStack.size > 20) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
    }

    private fun clearUndoRedo() {
        undoStack.clear()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = false, canRedo = false) }
    }

    fun undoPageEdit() {
        val active = _uiState.value.activePage ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(PageEditSnapshot(active.filterMode, active.customAdjustments))
        val restored = active.copy(filterMode = prev.filterMode, customAdjustments = prev.adjustments)
        updateActivePage(persist = true) { restored }
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
        reprocessEnhanced(restored)
    }

    fun redoPageEdit() {
        val active = _uiState.value.activePage ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(PageEditSnapshot(active.filterMode, active.customAdjustments))
        val restored = active.copy(filterMode = next.filterMode, customAdjustments = next.adjustments)
        updateActivePage(persist = true) { restored }
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
        reprocessEnhanced(restored)
    }

    fun resetPageEnhancement() {
        val active = _uiState.value.activePage ?: return
        pushUndoSnapshot(active)
        val reset = active.copy(filterMode = ScanFilterMode.ORIGINAL, customAdjustments = CustomAdjustments())
        updateActivePage(persist = true) { reset }
        reprocessEnhanced(reset)
    }

    fun setActiveFilter(mode: ScanFilterMode) {
        val active = _uiState.value.activePage ?: return
        pushUndoSnapshot(active)
        val updated = active.copy(filterMode = mode)
        updateActivePage(persist = true) { updated }
        reprocessEnhanced(updated)
    }

    fun setActiveCustomAdjustments(custom: CustomAdjustments) {
        val active = _uiState.value.activePage ?: return
        pushUndoSnapshot(active)
        val updated = active.copy(customAdjustments = custom)
        updateActivePage(persist = true) { updated }
        reprocessEnhanced(updated)
    }

    private fun reprocessEnhanced(item: ScannedPageItem) {
        val qMode = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Enhancing...") }
            withContext(ioDispatcher) {
                processor.enhanceDocument(
                    sourceFilePath = item.warpedImagePath,
                    outputFile = File(item.enhancedImagePath),
                    mode = item.filterMode,
                    custom = item.customAdjustments,
                    qualityMode = qMode
                )
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingMessage = "",
                    previewRevision = it.previewRevision + 1
                )
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

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Applying to ${allPages.size} pages...") }
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
                    isProcessing = false,
                    processingMessage = "",
                    statusMessage = "Applied ${mode.displayName} to all ${updatedPages.size} pages",
                    previewRevision = it.previewRevision + 1
                )
            }
            persistSessionSnapshot(updatedPages)
        }
    }

    fun selectPage(index: Int, targetStage: ScannerStage) {
        if (index in _uiState.value.pages.indices) {
            clearUndoRedo()
            _uiState.update { it.copy(activePageIndex = index, stage = targetStage) }
        }
    }

    fun retakeActivePage() {
        val idx = _uiState.value.activePageIndex
        startReplacePage(idx)
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

    fun rotatePageInQueue(index: Int, clockwise: Boolean = true) {
        val page = _uiState.value.pages.getOrNull(index) ?: return
        val qMode = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Rotating page...") }
            withContext(ioDispatcher) {
                processor.rotateFile90(page.rawImagePath, clockwise, qMode.maxProcessSide)
                processor.rotateFile90(page.warpedImagePath, clockwise, qMode.maxProcessSide)
                processor.rotateFile90(page.enhancedImagePath, clockwise, qMode.maxProcessSide)
            }
            val list = _uiState.value.pages.toMutableList()
            if (index in list.indices) {
                val q = if (clockwise) list[index].quad.rotated90Clockwise() else list[index].quad.rotated90CounterClockwise()
                list[index] = list[index].copy(quad = q)
            }
            _uiState.update {
                it.copy(
                    pages = list,
                    isProcessing = false,
                    processingMessage = "",
                    previewRevision = it.previewRevision + 1
                )
            }
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
            val nextStage = if (list.isEmpty()) ScannerStage.HUB else state.stage
            state.copy(pages = list, activePageIndex = nextIdx, stage = nextStage)
        }
        persistSessionSnapshot(list)
    }

    // --- Offline OCR (Tesseract4Android EN + HI) ---
    fun importTrainedData(uri: Uri, langCode: String) {
        viewModelScope.launch {
            val ok = ocrEngine.importTrainedDataFile(uri, langCode)
            refreshOcrStatus()
            _uiState.update {
                it.copy(statusMessage = if (ok) "Installed $langCode.traineddata locally!" else "Invalid traineddata file.")
            }
        }
    }

    fun runOfflineOcr() {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return
        val lang = _uiState.value.ocrLanguageCode

        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            _uiState.update { it.copy(isRunningOcr = true, ocrProgress = 0 to pages.size) }
            val result = ocrEngine.recognizePages(
                imagePaths = pages.map { it.enhancedImagePath },
                languageCode = lang,
                onProgress = { cur, total -> _uiState.update { it.copy(ocrProgress = cur to total) } }
            )
            when (result) {
                is OcrRunResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isRunningOcr = false,
                            ocrProgress = null,
                            ocrResults = result.pages,
                            statusMessage = "OCR completed for ${result.pages.size} page(s)."
                        )
                    }
                }
                is OcrRunResult.MissingLanguageData -> {
                    _uiState.update {
                        it.copy(
                            isRunningOcr = false,
                            ocrProgress = null,
                            statusMessage = "Missing offline OCR model(s): ${result.missingCodes.joinToString()}. Import .traineddata below."
                        )
                    }
                }
                is OcrRunResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isRunningOcr = false,
                            ocrProgress = null,
                            statusMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun cancelOcr() {
        ocrEngine.cancelOngoingOcr()
        ocrJob?.cancel()
        _uiState.update { it.copy(isRunningOcr = false, ocrProgress = null, statusMessage = "OCR cancelled.") }
    }

    fun updateOcrSearchQuery(q: String) {
        _uiState.update { it.copy(ocrSearchQuery = q) }
    }

    fun exportOcrTxtToUri(destUri: Uri) {
        val text = _uiState.value.combinedOcrText
        if (text.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            _uiState.update {
                it.copy(statusMessage = if (ok) "Exported OCR text (.txt)!" else "Failed to export TXT.")
            }
        }
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

            // If Searchable PDF requested, ensure we have positioned OCR words first!
            var wordsByPage: Map<Int, List<PositionedOcrWord>> = emptyMap()
            if (options.format == ExportFormat.SEARCHABLE_PDF) {
                if (_uiState.value.ocrResults.size == pages.size) {
                    wordsByPage = _uiState.value.ocrResults.associate { it.pageIndex to it.words }
                } else {
                    _uiState.update { it.copy(processingMessage = "Running offline OCR for Searchable PDF...") }
                    val ocrRes = ocrEngine.recognizePages(
                        imagePaths = pages.map { it.enhancedImagePath },
                        languageCode = _uiState.value.ocrLanguageCode,
                        onProgress = { cur, total -> _uiState.update { it.copy(exportProgress = cur to total) } }
                    )
                    when (ocrRes) {
                        is OcrRunResult.Success -> {
                            wordsByPage = ocrRes.pages.associate { it.pageIndex to it.words }
                            _uiState.update { it.copy(ocrResults = ocrRes.pages) }
                        }
                        is OcrRunResult.MissingLanguageData -> {
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    processingMessage = "",
                                    exportProgress = null,
                                    statusMessage = "Cannot build Searchable PDF: missing ${ocrRes.missingCodes.joinToString()} OCR data."
                                )
                            }
                            return@launch
                        }
                        is OcrRunResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    processingMessage = "",
                                    exportProgress = null,
                                    statusMessage = "OCR failed: ${ocrRes.message}"
                                )
                            }
                            return@launch
                        }
                    }
                }
            }

            _uiState.update { it.copy(processingMessage = "Writing & verifying file...") }
            val result = exportEngine.exportPages(
                pages = pages,
                options = options,
                destinationUri = destinationUri,
                ocrWordsByPageIndex = wordsByPage,
                onProgress = { cur, total -> _uiState.update { it.copy(exportProgress = cur to total) } }
            )

            when (result) {
                is ScanExportResult.Success -> {
                    var exportedPdfUri: String? = null
                    if (options.format == ExportFormat.PDF || options.format == ExportFormat.SEARCHABLE_PDF) {
                        val fileName = "${options.title.ifBlank { "Scanned_Document" }}.pdf"
                        storageRepository.registerScannedPdf(destinationUri, fileName, pages.size)
                        exportedPdfUri = destinationUri.toString()
                    }
                    withContext(ioDispatcher) {
                        scanSessionDao.clearAll()
                        sessionDir.listFiles()?.forEach { it.delete() }
                    }
                    val msg = when {
                        result.isZipArchive -> "Verified & exported all ${result.exportedPages} images as ZIP!"
                        options.format == ExportFormat.SEARCHABLE_PDF -> "Verified & exported ${result.exportedPages}-page Searchable PDF!"
                        else -> "Verified & exported ${result.exportedPages} page(s)!"
                    }
                    _uiState.update {
                        it.copy(
                            pages = emptyList(),
                            activePageIndex = 0,
                            ocrResults = emptyList(),
                            stage = ScannerStage.HUB,
                            isProcessing = false,
                            processingMessage = "",
                            exportProgress = null,
                            lastExportedPdfUri = exportedPdfUri,
                            statusMessage = msg
                        )
                    }
                }
                is ScanExportResult.Failure -> {
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

    fun clearStatusMessage() { _uiState.update { it.copy(statusMessage = null) } }
    fun clearLastExportedPdfUri() { _uiState.update { it.copy(lastExportedPdfUri = null) } }

    private fun updateActivePage(persist: Boolean, transform: (ScannedPageItem) -> ScannedPageItem) {
        val state = _uiState.value
        val idx = state.activePageIndex
        if (idx !in state.pages.indices) return
        val list = state.pages.toMutableList()
        list[idx] = transform(list[idx])
        _uiState.update { it.copy(pages = list) }
        if (persist) persistSessionSnapshot(list)
    }

    override fun onCleared() {
        runCatching { shutterSound.release() }
        ocrEngine.cancelOngoingOcr()
        super.onCleared()
    }
}
