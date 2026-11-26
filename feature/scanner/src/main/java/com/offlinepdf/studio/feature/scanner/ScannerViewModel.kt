package com.offlinepdf.studio.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.imageprocessing.CustomAdjustments
import com.offlinepdf.studio.core.imageprocessing.DocumentEdgeDetector
import com.offlinepdf.studio.core.imageprocessing.DocumentQuad
import com.offlinepdf.studio.core.imageprocessing.ExportFormat
import com.offlinepdf.studio.core.imageprocessing.ExportOptions
import com.offlinepdf.studio.core.imageprocessing.OpenCvDocumentProcessor
import com.offlinepdf.studio.core.imageprocessing.ScanExportEngine
import com.offlinepdf.studio.core.imageprocessing.ScanFilterMode
import com.offlinepdf.studio.core.imageprocessing.ScannedPageItem
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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
    val useFrontCamera: Boolean = false,
    val timerSeconds: Int = 0,
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val sessionDir: File by lazy {
        File(context.cacheDir, "scanner_session").apply { mkdirs() }
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

    fun toggleCameraLens() {
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera, flashEnabled = false) }
    }

    fun cycleTimer() {
        _uiState.update {
            val next = when (it.timerSeconds) {
                0 -> 3
                3 -> 5
                else -> 0
            }
            it.copy(timerSeconds = next)
        }
    }

    fun openStage(stage: ScannerStage) {
        _uiState.update { it.copy(stage = stage) }
    }

    fun onRawPhotoCaptured(rawFile: File, detectedQuad: DocumentQuad?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Detecting edges...") }
            val pageItem = withContext(ioDispatcher) {
                val id = UUID.randomUUID().toString().take(12)
                val quad = detectedQuad ?: run {
                    val bmp = processor.decodeDownsampledBitmap(rawFile.absolutePath, 640)
                    val found = bmp?.let { edgeDetector.detectDocumentQuad(it) }
                    bmp?.recycle()
                    found ?: DocumentQuad.fullFrame()
                }
                val warpedFile = File(sessionDir, "${id}_warped.jpg")
                val enhancedFile = File(sessionDir, "${id}_enhanced.jpg")

                processor.warpAndRotate(rawFile.absolutePath, warpedFile, quad, 0, 0f)
                processor.enhanceDocument(warpedFile.absolutePath, enhancedFile, ScanFilterMode.AUTO_COLOR)

                ScannedPageItem(
                    id = id,
                    rawImagePath = rawFile.absolutePath,
                    warpedImagePath = warpedFile.absolutePath,
                    enhancedImagePath = enhancedFile.absolutePath,
                    quad = quad,
                    filterMode = ScanFilterMode.AUTO_COLOR
                )
            }

            _uiState.update { state ->
                val updated = state.pages + pageItem
                state.copy(
                    pages = updated,
                    activePageIndex = updated.lastIndex,
                    stage = ScannerStage.CROP,
                    isProcessing = false,
                    processingMessage = "",
                    previewRevision = state.previewRevision + 1
                )
            }
        }
    }

    fun importGalleryImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Importing ${uris.size} image(s)...") }
            val imported = withContext(ioDispatcher) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val rawFile = createCaptureTempFile()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(rawFile).use { output -> input.copyTo(output) }
                        } ?: return@mapNotNull null

                        val bmp = processor.decodeDownsampledBitmap(rawFile.absolutePath, 640)
                        val quad = bmp?.let { edgeDetector.detectDocumentQuad(it) } ?: DocumentQuad.fullFrame()
                        bmp?.recycle()

                        val id = UUID.randomUUID().toString().take(12)
                        val warpedFile = File(sessionDir, "${id}_warped.jpg")
                        val enhancedFile = File(sessionDir, "${id}_enhanced.jpg")
                        processor.warpAndRotate(rawFile.absolutePath, warpedFile, quad, 0, 0f)
                        processor.enhanceDocument(warpedFile.absolutePath, enhancedFile, ScanFilterMode.AUTO_COLOR)

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

            _uiState.update { state ->
                val updated = state.pages + imported
                state.copy(
                    pages = updated,
                    activePageIndex = updated.lastIndex.coerceAtLeast(0),
                    stage = if (updated.isNotEmpty()) ScannerStage.PAGES_REVIEW else ScannerStage.CAMERA,
                    isProcessing = false,
                    processingMessage = "",
                    previewRevision = state.previewRevision + 1
                )
            }
        }
    }

    fun updateActiveQuad(quad: DocumentQuad) {
        updateActivePage { it.copy(quad = quad) }
    }

    fun resetActiveQuad() {
        updateActivePage { it.copy(quad = DocumentQuad.fullFrame(0.02f), fineRotationDegrees = 0f) }
    }

    fun autoDetectActiveQuad() {
        val active = _uiState.value.activePage ?: return
        viewModelScope.launch {
            val detected = withContext(ioDispatcher) {
                val bmp = processor.decodeDownsampledBitmap(active.rawImagePath, 640)
                val q = bmp?.let { edgeDetector.detectDocumentQuad(it) }
                bmp?.recycle()
                q ?: DocumentQuad.fullFrame()
            }
            updateActivePage { it.copy(quad = detected) }
        }
    }

    fun rotateActivePage90() {
        updateActivePage { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun setActiveFineRotation(degrees: Float) {
        updateActivePage { it.copy(fineRotationDegrees = degrees.coerceIn(-15f, 15f)) }
    }

    fun applyCropAndProceedToEnhance() {
        val active = _uiState.value.activePage ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Correcting perspective...") }
            withContext(ioDispatcher) {
                processor.warpAndRotate(
                    rawFilePath = active.rawImagePath,
                    outputFile = File(active.warpedImagePath),
                    quad = active.quad,
                    rotationDegrees = active.rotationDegrees,
                    fineRotationDegrees = active.fineRotationDegrees
                )
                processor.enhanceDocument(
                    sourceFilePath = active.warpedImagePath,
                    outputFile = File(active.enhancedImagePath),
                    mode = active.filterMode,
                    custom = active.customAdjustments
                )
            }
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

    fun setActiveFilter(mode: ScanFilterMode) {
        val active = _uiState.value.activePage ?: return
        updateActivePage { it.copy(filterMode = mode) }
        reprocessEnhanced(active.copy(filterMode = mode))
    }

    fun setActiveCustomAdjustments(custom: CustomAdjustments) {
        val active = _uiState.value.activePage ?: return
        val updated = active.copy(filterMode = ScanFilterMode.CUSTOM, customAdjustments = custom)
        updateActivePage { updated }
        reprocessEnhanced(updated)
    }

    private fun reprocessEnhanced(item: ScannedPageItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Enhancing...") }
            withContext(ioDispatcher) {
                processor.enhanceDocument(
                    sourceFilePath = item.warpedImagePath,
                    outputFile = File(item.enhancedImagePath),
                    mode = item.filterMode,
                    custom = item.customAdjustments
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
        if (allPages.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Applying filter to ${allPages.size} pages...") }
            val updatedPages = withContext(ioDispatcher) {
                allPages.map { p ->
                    val next = p.copy(filterMode = mode, customAdjustments = custom)
                    processor.enhanceDocument(next.warpedImagePath, File(next.enhancedImagePath), mode, custom)
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
            _uiState.update { state ->
                val list = state.pages.toMutableList()
                list.add((index + 1).coerceAtMost(list.size), copyItem)
                state.copy(pages = list, previewRevision = state.previewRevision + 1)
            }
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices || fromIndex == toIndex) {
                return@update state
            }
            val list = state.pages.toMutableList()
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            state.copy(pages = list, activePageIndex = toIndex)
        }
    }

    fun rotatePageInQueue(index: Int) {
        val page = _uiState.value.pages.getOrNull(index) ?: return
        val rotated = page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "Rotating page...") }
            withContext(ioDispatcher) {
                processor.warpAndRotate(
                    rotated.rawImagePath,
                    File(rotated.warpedImagePath),
                    rotated.quad,
                    rotated.rotationDegrees,
                    rotated.fineRotationDegrees
                )
                processor.enhanceDocument(
                    rotated.warpedImagePath,
                    File(rotated.enhancedImagePath),
                    rotated.filterMode,
                    rotated.customAdjustments
                )
            }
            _uiState.update { state ->
                val list = state.pages.toMutableList()
                if (index in list.indices) list[index] = rotated
                state.copy(
                    pages = list,
                    isProcessing = false,
                    processingMessage = "",
                    previewRevision = state.previewRevision + 1
                )
            }
        }
    }

    fun deletePage(index: Int) {
        val removed = _uiState.value.pages.getOrNull(index) ?: return
        viewModelScope.launch(ioDispatcher) {
            File(removed.rawImagePath).delete()
            File(removed.warpedImagePath).delete()
            File(removed.enhancedImagePath).delete()
        }
        _uiState.update { state ->
            val list = state.pages.toMutableList().apply { removeAt(index) }
            val nextIdx = state.activePageIndex.coerceAtMost((list.size - 1).coerceAtLeast(0))
            val nextStage = if (list.isEmpty()) ScannerStage.CAMERA else state.stage
            state.copy(pages = list, activePageIndex = nextIdx, stage = nextStage)
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
            _uiState.update { it.copy(isProcessing = true, exportProgress = 0 to pages.size) }
            val success = exportEngine.exportPages(
                pages = pages,
                options = options,
                destinationUri = destinationUri,
                onProgress = { cur, total ->
                    _uiState.update { it.copy(exportProgress = cur to total) }
                }
            )

            if (success) {
                var exportedPdfUri: String? = null
                if (options.format == ExportFormat.PDF) {
                    val fileName = "${options.title.ifBlank { "Scanned_Document" }}.pdf"
                    storageRepository.registerScannedPdf(destinationUri, fileName, pages.size)
                    exportedPdfUri = destinationUri.toString()
                }
                // Preserve originals until export succeeds; now clean temporary session files safely.
                withContext(ioDispatcher) {
                    sessionDir.listFiles()?.forEach { it.delete() }
                }
                _uiState.update {
                    it.copy(
                        pages = emptyList(),
                        activePageIndex = 0,
                        stage = ScannerStage.CAMERA,
                        isProcessing = false,
                        exportProgress = null,
                        lastExportedPdfUri = exportedPdfUri,
                        statusMessage = "Exported ${options.format.displayName} successfully!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        exportProgress = null,
                        statusMessage = "Export failed. Your scanned pages are preserved."
                    )
                }
            }
        }
    }

    suspend fun loadPreviewBitmap(path: String, maxSide: Int = 1080): Bitmap? =
        withContext(ioDispatcher) { processor.decodeDownsampledBitmap(path, maxSide) }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearLastExportedPdfUri() {
        _uiState.update { it.copy(lastExportedPdfUri = null) }
    }

    private fun updateActivePage(transform: (ScannedPageItem) -> ScannedPageItem) {
        _uiState.update { state ->
            val idx = state.activePageIndex
            if (idx !in state.pages.indices) return@update state
            val list = state.pages.toMutableList()
            list[idx] = transform(list[idx])
            state.copy(pages = list)
        }
    }
}
