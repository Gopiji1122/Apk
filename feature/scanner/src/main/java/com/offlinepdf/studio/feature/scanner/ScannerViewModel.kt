package com.offlinepdf.studio.feature.scanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.common.DefaultDispatcher
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.database.ScanSessionDao
import com.offlinepdf.studio.core.database.ScanSessionPageEntity
import com.offlinepdf.studio.core.database.UserPreferencesRepository
import com.offlinepdf.studio.core.imageprocessing.CleanupBrushMode
import com.offlinepdf.studio.core.imageprocessing.CleanupStroke
import com.offlinepdf.studio.core.imageprocessing.CustomAdjustments
import com.offlinepdf.studio.core.imageprocessing.DocumentEdgeDetector
import com.offlinepdf.studio.core.imageprocessing.DocumentQuad
import com.offlinepdf.studio.core.imageprocessing.ExportFormat
import com.offlinepdf.studio.core.imageprocessing.ExportOptions
import com.offlinepdf.studio.core.imageprocessing.ExportOrientation
import com.offlinepdf.studio.core.imageprocessing.ExportPageSize
import com.offlinepdf.studio.core.imageprocessing.MarkupStroke
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    CAMERA,
    REVIEW
}

enum class CapturePresetMode(val label: String) {
    WHITEBOARD("Whiteboard"),
    BOOK("Book"),
    DOCUMENT("Document"),
    ID_CARD("ID card"),
    BUSINESS_CARD("Business card")
}

enum class ReviewTool {
    NONE,
    CROP,
    ROTATE,
    FILTERS,
    ADJUST,
    CLEANUP,
    MARKUP,
    RESIZE,
    REORDER
}

data class PageEditSnapshot(
    val filterMode: ScanFilterMode,
    val customAdjustments: CustomAdjustments
)

data class ScannerUiState(
    val stage: ScannerStage = ScannerStage.CAMERA,
    val pages: List<ScannedPageItem> = emptyList(),
    val activePageIndex: Int = 0,
    val documentTitle: String = "",
    // Live camera state
    val capturePreset: CapturePresetMode = CapturePresetMode.DOCUMENT,
    val batchMode: Boolean = true,
    val autoCaptureEnabled: Boolean = true,
    val flashEnabled: Boolean = false,
    val qualityMode: ScanQualityMode = ScanQualityMode.FAST,
    val captureSoundEnabled: Boolean = true,
    val detectionSensitivity: String = "NORMAL",
    val liveDetectedQuad: DocumentQuad? = null,
    val liveConfidence: Float = 0f,
    val autoCaptureProgress: Float = 0f,
    val cameraQualityHint: String? = null,
    val idCardFrontPendingPath: String? = null,
    val retakingPageId: String? = null,
    val isCapturing: Boolean = false,
    // Review & Edit state
    val filmstripExpanded: Boolean = false,
    val activeTool: ReviewTool = ReviewTool.NONE,
    val pendingCropQuad: DocumentQuad = DocumentQuad.fullFrame(),
    val pendingFineRotation: Float = 0f,
    val compareOriginal: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    // Cleanup tool state (Surrounding Color + Custom Color)
    val cleanupMode: CleanupBrushMode = CleanupBrushMode.SURROUNDING_COLOR,
    val cleanupCustomColorArgb: Int = Color.WHITE,
    val cleanupBrushRadiusNorm: Float = 0.032f,
    val pendingCleanupStrokes: List<CleanupStroke> = emptyList(),
    // Markup tool state
    val markupColorArgb: Int = Color.parseColor("#E53935"),
    val markupIsHighlighter: Boolean = false,
    val markupStrokeWidthNorm: Float = 0.008f,
    val pendingMarkupStrokes: List<MarkupStroke> = emptyList(),
    // Settings & Export state
    val showSettingsSheet: Boolean = false,
    val showExportSheet: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val exportOptions: ExportOptions = ExportOptions(),
    val isProcessing: Boolean = false,
    val processingMessage: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val lastExportedPdfUri: String? = null,
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
    private val prefsRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val sessionDir: File by lazy {
        File(context.filesDir, "scan_sessions/active").apply { mkdirs() }
    }

    private val undoMap = mutableMapOf<String, MutableList<PageEditSnapshot>>()
    private val redoMap = mutableMapOf<String, MutableList<PageEditSnapshot>>()

    private val defaultTitle: String
        get() = "Scan " + SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

    private val _uiState = MutableStateFlow(ScannerUiState(documentTitle = defaultTitle))
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = prefsRepository.preferences.first()
            val savedFilter = runCatching { ScanFilterMode.valueOf(prefs.scannerDefaultFilter) }
                .getOrDefault(ScanFilterMode.AUTO_COLOR)
            _uiState.update {
                it.copy(
                    autoCaptureEnabled = prefs.scannerAutoCapture,
                    batchMode = prefs.scannerBatchMode,
                    flashEnabled = prefs.scannerDefaultFlash,
                    captureSoundEnabled = prefs.scannerCaptureSound,
                    detectionSensitivity = prefs.scannerDetectionSensitivity,
                    exportOptions = it.exportOptions.copy(
                        pageSize = ExportPageSize.A4,
                        jpegQuality = prefs.scannerDefaultPdfQuality.coerceIn(55, 96)
                    )
                )
            }
            // Restore any unfinished persistent session silently so pages are never lost
            val savedPages = withContext(ioDispatcher) { scanSessionDao.getAllOrdered() }
                .mapNotNull { entity ->
                    if (!File(entity.rawImagePath).exists()) return@mapNotNull null
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
                        filterMode = runCatching { ScanFilterMode.valueOf(entity.filterModeName) }.getOrDefault(savedFilter),
                        customAdjustments = CustomAdjustments(
                            brightness = entity.brightness,
                            contrast = entity.contrast,
                            sharpness = entity.sharpness,
                            filterStrength = entity.saturation.coerceIn(0f, 1f)
                        )
                    )
                }
            if (savedPages.isNotEmpty()) {
                _uiState.update { it.copy(pages = savedPages, activePageIndex = savedPages.lastIndex) }
            }
        }
    }

    private fun persistSession(pages: List<ScannedPageItem>) {
        viewModelScope.launch(ioDispatcher) {
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
            scanSessionDao.clearAll()
            if (entities.isNotEmpty()) scanSessionDao.upsertAll(entities)
        }
    }

    // ---- Camera Controls ----
    fun selectCapturePreset(preset: CapturePresetMode) {
        _uiState.update { it.copy(capturePreset = preset, idCardFrontPendingPath = null) }
    }

    fun setBatchMode(batch: Boolean) {
        _uiState.update { it.copy(batchMode = batch) }
        viewModelScope.launch { prefsRepository.setScannerBatchMode(batch) }
    }

    fun toggleAutoCapture() {
        val next = !_uiState.value.autoCaptureEnabled
        _uiState.update { it.copy(autoCaptureEnabled = next, autoCaptureProgress = 0f) }
        viewModelScope.launch { prefsRepository.setScannerAutoCapture(next) }
    }

    fun toggleFlash() {
        _uiState.update { it.copy(flashEnabled = !it.flashEnabled) }
    }

    fun toggleQualityMode() {
        val next = if (_uiState.value.qualityMode == ScanQualityMode.FAST) ScanQualityMode.HD else ScanQualityMode.FAST
        _uiState.update {
            it.copy(
                qualityMode = next,
                statusMessage = if (next == ScanQualityMode.HD) "HD Mode: 3200px high-detail capture" else "Fast Mode: instant capture"
            )
        }
        
    }

    fun toggleCaptureSound(enabled: Boolean) {
        _uiState.update { it.copy(captureSoundEnabled = enabled) }
        viewModelScope.launch { prefsRepository.setScannerCaptureSound(enabled) }
    }

    fun setDetectionSensitivity(level: String) {
        _uiState.update { it.copy(detectionSensitivity = level) }
        viewModelScope.launch { prefsRepository.setScannerDetectionSensitivity(level) }
    }

    fun openSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun updateLiveDetection(quad: DocumentQuad?, confidence: Float, progress: Float, hint: String?) {
        _uiState.update {
            it.copy(
                liveDetectedQuad = quad,
                liveConfidence = confidence,
                autoCaptureProgress = progress,
                cameraQualityHint = hint
            )
        }
    }

    fun createCaptureOutputFile(): File = File(sessionDir, "raw_${UUID.randomUUID()}.jpg")

    fun setCapturing(capturing: Boolean) {
        _uiState.update { it.copy(isCapturing = capturing) }
    }

    fun openReviewScreen() {
        if (_uiState.value.pages.isEmpty()) return
        val idx = _uiState.value.activePageIndex.coerceIn(0, _uiState.value.pages.lastIndex)
        val page = _uiState.value.pages[idx]
        _uiState.update {
            it.copy(
                stage = ScannerStage.REVIEW,
                activePageIndex = idx,
                activeTool = ReviewTool.NONE,
                pendingCropQuad = page.quad,
                pendingFineRotation = page.fineRotationDegrees,
                compareOriginal = false
            )
        }
        refreshUndoRedoFlags()
    }

    fun openCameraToAddPage() {
        _uiState.update {
            it.copy(
                stage = ScannerStage.CAMERA,
                activeTool = ReviewTool.NONE,
                retakingPageId = null,
                liveDetectedQuad = null,
                autoCaptureProgress = 0f
            )
        }
    }

    fun startRetakeActivePage() {
        val page = _uiState.value.activePage ?: return
        _uiState.update {
            it.copy(
                stage = ScannerStage.CAMERA,
                activeTool = ReviewTool.NONE,
                retakingPageId = page.id,
                liveDetectedQuad = null,
                autoCaptureProgress = 0f,
                statusMessage = "Retaking Page ${_uiState.value.activePageIndex + 1}"
            )
        }
    }

    private fun defaultFilterForPreset(preset: CapturePresetMode): ScanFilterMode = when (preset) {
        CapturePresetMode.WHITEBOARD -> ScanFilterMode.ENHANCED_DOCUMENT
        CapturePresetMode.BOOK -> ScanFilterMode.AUTO_COLOR
        CapturePresetMode.DOCUMENT -> ScanFilterMode.AUTO_COLOR
        CapturePresetMode.ID_CARD -> ScanFilterMode.AUTO_COLOR
        CapturePresetMode.BUSINESS_CARD -> ScanFilterMode.AUTO_COLOR
    }

    fun onPhotoCaptured(rawFile: File) {
        viewModelScope.launch {
            val quality = _uiState.value.qualityMode
            val preset = _uiState.value.capturePreset
            val retakingId = _uiState.value.retakingPageId
            val idFrontPending = _uiState.value.idCardFrontPendingPath
            _uiState.update {
                it.copy(
                    isCapturing = false,
                    isProcessing = true,
                    processingMessage = if (quality == ScanQualityMode.HD) "Processing HD scan..." else "Enhancing page..."
                )
            }

            val resultPages = withContext(defaultDispatcher) {
                processor.normalizeExifOrientation(rawFile.absolutePath, maxSide = if (quality == ScanQualityMode.HD) 3200 else 2200)
                val previewBmp = processor.decodeDownsampledBitmap(rawFile.absolutePath, maxSide = 960)
                val detectedQuad = previewBmp?.let { bmp ->
                    try {
                        edgeDetector.detectDocumentQuad(bmp)
                    } finally {
                        bmp.recycle()
                    }
                } ?: _uiState.value.liveDetectedQuad ?: DocumentQuad.fullFrame()

                val pageId = retakingId ?: UUID.randomUUID().toString()
                val croppedFile = File(sessionDir, "crop_${pageId}.jpg")
                val enhancedFile = File(sessionDir, "enh_${pageId}.jpg")
                val filter = defaultFilterForPreset(preset)

                processor.warpAndRotate(rawFile.absolutePath, croppedFile, detectedQuad, 0, 0f, quality)
                processor.enhanceDocument(croppedFile.absolutePath, enhancedFile, filter, CustomAdjustments(), quality)

                // Handle ID Card 2-sided flow
                if (preset == CapturePresetMode.ID_CARD && retakingId == null) {
                    if (idFrontPending == null) {
                        return@withContext Pair<List<ScannedPageItem>, String?>(emptyList(), enhancedFile.absolutePath)
                    } else {
                        val combinedId = UUID.randomUUID().toString()
                        val combinedFile = File(sessionDir, "id_${combinedId}.jpg")
                        processor.combineIdCardFrontBack(idFrontPending, enhancedFile.absolutePath, combinedFile)
                        val combinedItem = ScannedPageItem(
                            id = combinedId,
                            rawImagePath = combinedFile.absolutePath,
                            warpedImagePath = combinedFile.absolutePath,
                            enhancedImagePath = combinedFile.absolutePath,
                            quad = DocumentQuad.fullFrame(),
                            filterMode = ScanFilterMode.ORIGINAL
                        )
                        return@withContext Pair(listOf(combinedItem), null)
                    }
                }

                // Handle Book Mode 2-page spread auto-split
                if (preset == CapturePresetMode.BOOK && retakingId == null) {
                    val leftId = UUID.randomUUID().toString()
                    val rightId = UUID.randomUUID().toString()
                    val leftCrop = File(sessionDir, "crop_${leftId}.jpg")
                    val rightCrop = File(sessionDir, "crop_${rightId}.jpg")
                    val leftEnh = File(sessionDir, "enh_${leftId}.jpg")
                    val rightEnh = File(sessionDir, "enh_${rightId}.jpg")
                    if (processor.splitBookSpread(croppedFile.absolutePath, leftCrop, rightCrop, quality)) {
                        processor.enhanceDocument(leftCrop.absolutePath, leftEnh, filter, CustomAdjustments(), quality)
                        processor.enhanceDocument(rightCrop.absolutePath, rightEnh, filter, CustomAdjustments(), quality)
                        return@withContext Pair(
                            listOf(
                                ScannedPageItem(leftId, leftCrop.absolutePath, leftCrop.absolutePath, leftEnh.absolutePath, DocumentQuad.fullFrame(), filterMode = filter),
                                ScannedPageItem(rightId, rightCrop.absolutePath, rightCrop.absolutePath, rightEnh.absolutePath, DocumentQuad.fullFrame(), filterMode = filter)
                            ),
                            null
                        )
                    }
                }

                val item = ScannedPageItem(
                    id = pageId,
                    rawImagePath = rawFile.absolutePath,
                    warpedImagePath = croppedFile.absolutePath,
                    enhancedImagePath = enhancedFile.absolutePath,
                    quad = detectedQuad,
                    filterMode = filter
                )
                Pair(listOf(item), null)
            }

            // ID Card Front captured -> stay on camera and prompt for Back side
            if (resultPages.second != null) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingMessage = null,
                        idCardFrontPendingPath = resultPages.second,
                        statusMessage = "Front captured — now scan Back of ID card"
                    )
                }
                return@launch
            }

            val newItems = resultPages.first
            if (retakingId != null && newItems.isNotEmpty()) {
                val replaced = _uiState.value.pages.map { if (it.id == retakingId) newItems.first() else it }
                val idx = replaced.indexOfFirst { it.id == retakingId }.coerceAtLeast(0)
                _uiState.update {
                    it.copy(
                        pages = replaced,
                        activePageIndex = idx,
                        retakingPageId = null,
                        stage = ScannerStage.REVIEW,
                        activeTool = ReviewTool.NONE,
                        isProcessing = false,
                        processingMessage = null,
                        previewRevision = it.previewRevision + 1,
                        statusMessage = "Page ${idx + 1} updated"
                    )
                }
                persistSession(replaced)
            } else if (newItems.isNotEmpty()) {
                val updated = _uiState.value.pages + newItems
                val goDirectToReview = !_uiState.value.batchMode
                _uiState.update {
                    it.copy(
                        pages = updated,
                        activePageIndex = updated.lastIndex,
                        idCardFrontPendingPath = null,
                        stage = if (goDirectToReview) ScannerStage.REVIEW else ScannerStage.CAMERA,
                        activeTool = ReviewTool.NONE,
                        pendingCropQuad = updated.last().quad,
                        isProcessing = false,
                        processingMessage = null,
                        previewRevision = it.previewRevision + 1,
                        statusMessage = if (goDirectToReview) null else "Captured (${updated.size})"
                    )
                }
                persistSession(updated)
            } else {
                _uiState.update { it.copy(isProcessing = false, processingMessage = null) }
            }
        }
    }

    fun importImagesFromGallery(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Importing ${uris.size} image(s)...") }
            val quality = _uiState.value.qualityMode
            val imported = withContext(ioDispatcher) {
                val list = mutableListOf<ScannedPageItem>()
                for (uri in uris) {
                    val raw = createCaptureOutputFile()
                    val copied = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(raw).use { out -> input.copyTo(out) }
                        } != null
                    }.getOrDefault(false)
                    if (!copied) continue
                    processor.normalizeExifOrientation(raw.absolutePath)
                    val previewBmp = processor.decodeDownsampledBitmap(raw.absolutePath, maxSide = 960)
                    val quad = previewBmp?.let { b -> try { edgeDetector.detectDocumentQuad(b) } finally { b.recycle() } } ?: DocumentQuad.fullFrame()
                    val id = UUID.randomUUID().toString()
                    val cropFile = File(sessionDir, "crop_${id}.jpg")
                    val enhFile = File(sessionDir, "enh_${id}.jpg")
                    processor.warpAndRotate(raw.absolutePath, cropFile, quad, 0, 0f, quality)
                    processor.enhanceDocument(cropFile.absolutePath, enhFile, ScanFilterMode.AUTO_COLOR, CustomAdjustments(), quality)
                    list.add(
                        ScannedPageItem(
                            id = id,
                            rawImagePath = raw.absolutePath,
                            warpedImagePath = cropFile.absolutePath,
                            enhancedImagePath = enhFile.absolutePath,
                            quad = quad,
                            filterMode = ScanFilterMode.AUTO_COLOR
                        )
                    )
                }
                list
            }
            val updated = _uiState.value.pages + imported
            _uiState.update {
                it.copy(
                    pages = updated,
                    activePageIndex = updated.lastIndex.coerceAtLeast(0),
                    stage = if (updated.isNotEmpty()) ScannerStage.REVIEW else it.stage,
                    isProcessing = false,
                    processingMessage = null,
                    previewRevision = it.previewRevision + 1,
                    statusMessage = "Imported ${imported.size} page(s)"
                )
            }
            persistSession(updated)
        }
    }

    // ---- Review & Navigation ----
    fun selectPage(index: Int) {
        val pages = _uiState.value.pages
        if (index !in pages.indices) return
        val p = pages[index]
        _uiState.update {
            it.copy(
                activePageIndex = index,
                pendingCropQuad = p.quad,
                pendingFineRotation = p.fineRotationDegrees,
                pendingCleanupStrokes = emptyList(),
                pendingMarkupStrokes = emptyList(),
                compareOriginal = false
            )
        }
        refreshUndoRedoFlags()
    }

    fun previousPage() = selectPage((_uiState.value.activePageIndex - 1).coerceAtLeast(0))
    fun nextPage() = selectPage((_uiState.value.activePageIndex + 1).coerceAtMost((_uiState.value.pages.size - 1).coerceAtLeast(0)))

    fun toggleFilmstrip() {
        _uiState.update { it.copy(filmstripExpanded = !it.filmstripExpanded) }
    }

    fun openRenameDialog(show: Boolean) {
        _uiState.update { it.copy(showRenameDialog = show) }
    }

    fun updateDocumentTitle(title: String) {
        val clean = title.trim().ifBlank { defaultTitle }
        _uiState.update { it.copy(documentTitle = clean, showRenameDialog = false) }
    }

    fun selectReviewTool(tool: ReviewTool) {
        val page = _uiState.value.activePage
        val next = if (_uiState.value.activeTool == tool) ReviewTool.NONE else tool
        _uiState.update {
            it.copy(
                activeTool = next,
                pendingCropQuad = page?.quad ?: DocumentQuad.fullFrame(),
                pendingFineRotation = page?.fineRotationDegrees ?: 0f,
                pendingCleanupStrokes = emptyList(),
                pendingMarkupStrokes = emptyList(),
                compareOriginal = false
            )
        }
    }

    // ---- Crop & Rotate ----
    fun updatePendingCropQuad(quad: DocumentQuad) {
        _uiState.update { it.copy(pendingCropQuad = quad) }
    }

    fun autoDetectActivePageCrop() {
        val page = _uiState.value.activePage ?: return
        viewModelScope.launch {
            val detected = withContext(defaultDispatcher) {
                val bmp = processor.decodeDownsampledBitmap(page.rawImagePath, 960) ?: return@withContext null
                try { edgeDetector.detectDocumentQuad(bmp) } finally { bmp.recycle() }
            }
            if (detected != null) {
                _uiState.update { it.copy(pendingCropQuad = detected, statusMessage = "Corners detected") }
            } else {
                _uiState.update { it.copy(pendingCropQuad = DocumentQuad.fullFrame(), statusMessage = "Using full frame") }
            }
        }
    }

    fun resetCropToFull() {
        _uiState.update { it.copy(pendingCropQuad = DocumentQuad.fullFrame()) }
    }

    fun applyCropAndPerspective() {
        val page = _uiState.value.activePage ?: return
        val quad = _uiState.value.pendingCropQuad
        val quality = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Applying crop...") }
            val updated = withContext(defaultDispatcher) {
                val cropFile = File(page.warpedImagePath)
                val enhFile = File(page.enhancedImagePath)
                processor.warpAndRotate(page.rawImagePath, cropFile, quad, page.rotationDegrees, page.fineRotationDegrees, quality)
                processor.enhanceDocument(cropFile.absolutePath, enhFile, page.filterMode, page.customAdjustments, quality)
                page.copy(quad = quad)
            }
            replaceActivePage(updated, closeTool = true)
        }
    }

    fun updatePendingFineRotation(deg: Float) {
        _uiState.update { it.copy(pendingFineRotation = deg.coerceIn(-45f, 45f)) }
    }

    fun rotateActivePage90(clockwise: Boolean) {
        val page = _uiState.value.activePage ?: return
        val quality = _uiState.value.qualityMode
        val nextRot = ((page.rotationDegrees + if (clockwise) 90 else -90) % 360 + 360) % 360
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Rotating...") }
            val updated = withContext(defaultDispatcher) {
                val cropFile = File(page.warpedImagePath)
                val enhFile = File(page.enhancedImagePath)
                processor.warpAndRotate(page.rawImagePath, cropFile, page.quad, nextRot, _uiState.value.pendingFineRotation, quality)
                processor.enhanceDocument(cropFile.absolutePath, enhFile, page.filterMode, page.customAdjustments, quality)
                page.copy(rotationDegrees = nextRot, fineRotationDegrees = _uiState.value.pendingFineRotation)
            }
            replaceActivePage(updated, closeTool = false)
        }
    }

    fun applyFineRotation() {
        val page = _uiState.value.activePage ?: return
        val fine = _uiState.value.pendingFineRotation
        val quality = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Straightening...") }
            val updated = withContext(defaultDispatcher) {
                val cropFile = File(page.warpedImagePath)
                val enhFile = File(page.enhancedImagePath)
                processor.warpAndRotate(page.rawImagePath, cropFile, page.quad, page.rotationDegrees, fine, quality)
                processor.enhanceDocument(cropFile.absolutePath, enhFile, page.filterMode, page.customAdjustments, quality)
                page.copy(fineRotationDegrees = fine)
            }
            replaceActivePage(updated, closeTool = true)
        }
    }

    // ---- Filters & Adjustments ----
    fun quickAutoCleanActivePage() {
        applyFilterToActivePage(ScanFilterMode.AUTO_COLOR)
    }

    fun applyFilterToActivePage(mode: ScanFilterMode) {
        val page = _uiState.value.activePage ?: return
        pushUndo(page)
        reprocessEnhancement(page.copy(filterMode = mode))
    }

    fun applyFilterToAllPages(mode: ScanFilterMode) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return
        val quality = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Applying ${mode.displayName} to all pages...") }
            val updated = withContext(defaultDispatcher) {
                pages.map { p ->
                    processor.enhanceDocument(p.warpedImagePath, File(p.enhancedImagePath), mode, p.customAdjustments, quality)
                    p.copy(filterMode = mode)
                }
            }
            _uiState.update {
                it.copy(
                    pages = updated,
                    isProcessing = false,
                    processingMessage = null,
                    previewRevision = it.previewRevision + 1,
                    statusMessage = "Applied ${mode.displayName} to ${updated.size} page(s)"
                )
            }
            persistSession(updated)
        }
    }

    fun updateCustomAdjustments(adjustments: CustomAdjustments) {
        val page = _uiState.value.activePage ?: return
        pushUndo(page)
        reprocessEnhancement(page.copy(customAdjustments = adjustments))
    }

    fun toggleCompareOriginal(show: Boolean) {
        _uiState.update { it.copy(compareOriginal = show) }
    }

    fun resetActivePageEdits() {
        val page = _uiState.value.activePage ?: return
        pushUndo(page)
        reprocessEnhancement(page.copy(filterMode = ScanFilterMode.AUTO_COLOR, customAdjustments = CustomAdjustments()))
    }

    fun undoActivePageEdit() {
        val page = _uiState.value.activePage ?: return
        val stack = undoMap[page.id] ?: return
        if (stack.isEmpty()) return
        val prev = stack.removeAt(stack.lastIndex)
        redoMap.getOrPut(page.id) { mutableListOf() }.add(PageEditSnapshot(page.filterMode, page.customAdjustments))
        reprocessEnhancement(page.copy(filterMode = prev.filterMode, customAdjustments = prev.customAdjustments))
    }

    fun redoActivePageEdit() {
        val page = _uiState.value.activePage ?: return
        val stack = redoMap[page.id] ?: return
        if (stack.isEmpty()) return
        val next = stack.removeAt(stack.lastIndex)
        undoMap.getOrPut(page.id) { mutableListOf() }.add(PageEditSnapshot(page.filterMode, page.customAdjustments))
        reprocessEnhancement(page.copy(filterMode = next.filterMode, customAdjustments = next.customAdjustments))
    }

    private fun pushUndo(page: ScannedPageItem) {
        undoMap.getOrPut(page.id) { mutableListOf() }.add(PageEditSnapshot(page.filterMode, page.customAdjustments))
        redoMap[page.id]?.clear()
        refreshUndoRedoFlags()
    }

    private fun refreshUndoRedoFlags() {
        val id = _uiState.value.activePage?.id
        _uiState.update {
            it.copy(
                canUndo = id != null && (undoMap[id]?.isNotEmpty() == true),
                canRedo = id != null && (redoMap[id]?.isNotEmpty() == true)
            )
        }
    }

    private fun reprocessEnhancement(target: ScannedPageItem) {
        val quality = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Enhancing...") }
            withContext(defaultDispatcher) {
                processor.enhanceDocument(
                    target.warpedImagePath,
                    File(target.enhancedImagePath),
                    target.filterMode,
                    target.customAdjustments,
                    quality
                )
            }
            replaceActivePage(target, closeTool = false)
            refreshUndoRedoFlags()
        }
    }

    // ---- Cleanup Tool (Surrounding Color Inpaint + Custom Color) ----
    fun setCleanupMode(mode: CleanupBrushMode) {
        _uiState.update { it.copy(cleanupMode = mode) }
    }

    fun setCleanupCustomColor(argb: Int) {
        _uiState.update { it.copy(cleanupCustomColorArgb = argb, cleanupMode = CleanupBrushMode.CUSTOM_COLOR) }
    }

    fun setCleanupBrushRadius(radiusNorm: Float) {
        _uiState.update { it.copy(cleanupBrushRadiusNorm = radiusNorm.coerceIn(0.014f, 0.085f)) }
    }

    fun addCleanupStroke(points: List<NormalizedPoint>) {
        if (points.isEmpty()) return
        val s = _uiState.value
        val stroke = CleanupStroke(
            points = points,
            brushRadiusNorm = s.cleanupBrushRadiusNorm,
            mode = s.cleanupMode,
            customColorArgb = s.cleanupCustomColorArgb
        )
        _uiState.update { it.copy(pendingCleanupStrokes = it.pendingCleanupStrokes + stroke) }
    }

    fun undoCleanupStroke() {
        _uiState.update {
            if (it.pendingCleanupStrokes.isEmpty()) it
            else it.copy(pendingCleanupStrokes = it.pendingCleanupStrokes.dropLast(1))
        }
    }

    fun clearCleanupStrokes() {
        _uiState.update { it.copy(pendingCleanupStrokes = emptyList()) }
    }

    fun applyCleanupStrokes() {
        val page = _uiState.value.activePage ?: return
        val strokes = _uiState.value.pendingCleanupStrokes
        if (strokes.isEmpty()) {
            _uiState.update { it.copy(activeTool = ReviewTool.NONE) }
            return
        }
        val quality = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Cleaning up marks...") }
            val ok = withContext(defaultDispatcher) {
                processor.applyCleanupStrokes(page.enhancedImagePath, File(page.enhancedImagePath), strokes, quality)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingMessage = null,
                    pendingCleanupStrokes = emptyList(),
                    activeTool = ReviewTool.NONE,
                    previewRevision = it.previewRevision + 1,
                    statusMessage = if (ok) "Cleanup applied" else "Cleanup failed"
                )
            }
        }
    }

    // ---- Markup Tool ----
    fun setMarkupColor(argb: Int) {
        _uiState.update { it.copy(markupColorArgb = argb) }
    }

    fun setMarkupHighlighter(highlighter: Boolean) {
        _uiState.update {
            it.copy(
                markupIsHighlighter = highlighter,
                markupStrokeWidthNorm = if (highlighter) 0.028f else 0.008f
            )
        }
    }

    fun addMarkupStroke(points: List<NormalizedPoint>) {
        if (points.size < 2) return
        val s = _uiState.value
        val stroke = MarkupStroke(
            points = points,
            strokeWidthNorm = s.markupStrokeWidthNorm,
            colorArgb = s.markupColorArgb,
            isHighlighter = s.markupIsHighlighter
        )
        _uiState.update { it.copy(pendingMarkupStrokes = it.pendingMarkupStrokes + stroke) }
    }

    fun undoMarkupStroke() {
        _uiState.update {
            if (it.pendingMarkupStrokes.isEmpty()) it
            else it.copy(pendingMarkupStrokes = it.pendingMarkupStrokes.dropLast(1))
        }
    }

    fun applyMarkupStrokes() {
        val page = _uiState.value.activePage ?: return
        val strokes = _uiState.value.pendingMarkupStrokes
        if (strokes.isEmpty()) {
            _uiState.update { it.copy(activeTool = ReviewTool.NONE) }
            return
        }
        val quality = _uiState.value.qualityMode
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Saving markup...") }
            val ok = withContext(defaultDispatcher) {
                processor.applyMarkupStrokes(page.enhancedImagePath, File(page.enhancedImagePath), strokes, quality)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingMessage = null,
                    pendingMarkupStrokes = emptyList(),
                    activeTool = ReviewTool.NONE,
                    previewRevision = it.previewRevision + 1,
                    statusMessage = if (ok) "Markup saved" else "Markup failed"
                )
            }
        }
    }

    // ---- Reorder / Duplicate / Delete ----
    fun movePage(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.pages.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _uiState.update { it.copy(pages = list, activePageIndex = toIndex) }
        persistSession(list)
    }

    fun duplicatePage(index: Int) {
        val src = _uiState.value.pages.getOrNull(index) ?: return
        viewModelScope.launch {
            val copy = withContext(ioDispatcher) {
                val id = UUID.randomUUID().toString()
                val rawCopy = File(sessionDir, "raw_${id}.jpg")
                val cropCopy = File(sessionDir, "crop_${id}.jpg")
                val enhCopy = File(sessionDir, "enh_${id}.jpg")
                File(src.rawImagePath).copyTo(rawCopy, overwrite = true)
                File(src.warpedImagePath).copyTo(cropCopy, overwrite = true)
                File(src.enhancedImagePath).copyTo(enhCopy, overwrite = true)
                src.copy(
                    id = id,
                    rawImagePath = rawCopy.absolutePath,
                    warpedImagePath = cropCopy.absolutePath,
                    enhancedImagePath = enhCopy.absolutePath
                )
            }
            val list = _uiState.value.pages.toMutableList()
            list.add(index + 1, copy)
            _uiState.update { it.copy(pages = list, activePageIndex = index + 1, statusMessage = "Page duplicated") }
            persistSession(list)
        }
    }

    fun confirmDeleteActivePage(show: Boolean) {
        _uiState.update { it.copy(showDeleteConfirm = show) }
    }

    fun deleteActivePage() {
        val idx = _uiState.value.activePageIndex
        deletePageAt(idx)
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun deletePageAt(index: Int) {
        val list = _uiState.value.pages.toMutableList()
        if (index !in list.indices) return
        val removed = list.removeAt(index)
        viewModelScope.launch(ioDispatcher) {
            runCatching { File(removed.rawImagePath).delete() }
            runCatching { File(removed.warpedImagePath).delete() }
            runCatching { File(removed.enhancedImagePath).delete() }
        }
        val nextIdx = index.coerceAtMost((list.size - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                pages = list,
                activePageIndex = nextIdx,
                stage = if (list.isEmpty()) ScannerStage.CAMERA else it.stage,
                activeTool = if (list.isEmpty()) ReviewTool.NONE else it.activeTool
            )
        }
        persistSession(list)
    }

    private fun replaceActivePage(updated: ScannedPageItem, closeTool: Boolean) {
        val idx = _uiState.value.activePageIndex
        val list = _uiState.value.pages.toMutableList()
        if (idx !in list.indices) return
        list[idx] = updated
        _uiState.update {
            it.copy(
                pages = list,
                isProcessing = false,
                processingMessage = null,
                activeTool = if (closeTool) ReviewTool.NONE else it.activeTool,
                previewRevision = it.previewRevision + 1
            )
        }
        persistSession(list)
    }

    // ---- Export & Share ----
    fun updateExportOptions(options: ExportOptions) {
        _uiState.update { it.copy(exportOptions = options) }
    }

    fun openExportSheet(show: Boolean) {
        _uiState.update { it.copy(showExportSheet = show) }
    }

    fun suggestedExportFileName(format: ExportFormat = _uiState.value.exportOptions.format): String {
        val base = _uiState.value.documentTitle.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "Scan" }
        val multi = _uiState.value.pages.size > 1
        return when (format) {
            ExportFormat.PDF, ExportFormat.SEARCHABLE_PDF -> "$base.pdf"
            ExportFormat.JPEG -> if (multi) "${base}_JPEG.zip" else "$base.jpg"
            ExportFormat.PNG -> if (multi) "${base}_PNG.zip" else "$base.png"
        }
    }

    fun suggestedExportMimeType(format: ExportFormat = _uiState.value.exportOptions.format): String {
        val multi = _uiState.value.pages.size > 1
        return when (format) {
            ExportFormat.PDF, ExportFormat.SEARCHABLE_PDF -> "application/pdf"
            ExportFormat.JPEG -> if (multi) "application/zip" else "image/jpeg"
            ExportFormat.PNG -> if (multi) "application/zip" else "image/png"
        }
    }

    fun exportToUri(destinationUri: Uri, formatOverride: ExportFormat? = null) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return
        val options = _uiState.value.exportOptions.let {
            if (formatOverride != null) it.copy(format = formatOverride) else it
        }
        viewModelScope.launch {
            _uiState.update { it.copy(showExportSheet = false, isProcessing = true, processingMessage = "Saving ${options.format.name}...") }
            val result = withContext(ioDispatcher) {
                exportEngine.exportPages(
                    pages = pages,
                    options = options,
                    destinationUri = destinationUri,
                    onProgress = { cur, tot ->
                        _uiState.update { it.copy(processingMessage = "Exporting page $cur of $tot...") }
                    }
                )
            }
            when (result) {
                is ScanExportResult.Success -> {
                    if (options.format == ExportFormat.PDF || options.format == ExportFormat.SEARCHABLE_PDF) {
                        runCatching {
                            storageRepository.registerScannedPdf(
                                uri = destinationUri,
                                fallbackName = suggestedExportFileName(ExportFormat.PDF),
                                pageCount = result.exportedPages
                            )
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            processingMessage = null,
                            lastExportedPdfUri = if (options.format == ExportFormat.PDF) destinationUri.toString() else null,
                            statusMessage = "Saved ${result.exportedPages} page(s) safely"
                        )
                    }
                }
                is ScanExportResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            processingMessage = null,
                            errorMessage = result.reason
                        )
                    }
                }
            }
        }
    }

    fun buildSharePdfIntent(onReady: (Intent?) -> Unit) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) { onReady(null); return }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Preparing PDF to share...") }
            val intent = withContext(ioDispatcher) {
                val shareDir = File(context.cacheDir, "shared_scans").apply { mkdirs() }
                val outFile = File(shareDir, suggestedExportFileName(ExportFormat.PDF))
                val fileUri = Uri.fromFile(outFile)
                val res = exportEngine.exportPages(pages, _uiState.value.exportOptions.copy(format = ExportFormat.PDF), fileUri)
                if (res !is ScanExportResult.Success) return@withContext null
                val contentUri = runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                }.getOrNull() ?: return@withContext null
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            _uiState.update { it.copy(isProcessing = false, processingMessage = null) }
            onReady(intent)
        }
    }

    fun clearSessionAndReturnHome(onHome: () -> Unit) {
        onHome()
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
