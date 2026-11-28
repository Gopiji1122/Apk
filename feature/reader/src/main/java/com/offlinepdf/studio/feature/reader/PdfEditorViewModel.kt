package com.offlinepdf.studio.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.pdf.MuPdfAnnotationEngine
import com.offlinepdf.studio.core.pdf.PdfAnnotationCoordinateMath
import com.offlinepdf.studio.core.pdf.PdfAnnotationTool
import com.offlinepdf.studio.core.pdf.PdfDocumentHandle
import com.offlinepdf.studio.core.pdf.PdfEditAnnotation
import com.offlinepdf.studio.core.pdf.PdfEditorMode
import com.offlinepdf.studio.core.pdf.PdfEngine
import com.offlinepdf.studio.core.pdf.PdfMarkupKind
import com.offlinepdf.studio.core.pdf.PdfNormPoint
import com.offlinepdf.studio.core.pdf.PdfOpenResult
import com.offlinepdf.studio.core.pdf.PdfShapeKind
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
import java.io.FileInputStream
import javax.inject.Inject

data class PdfEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val documentTitle: String = "Document",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val currentPageBitmap: Bitmap? = null,
    val currentPageAspectRatio: Float = 0.707f,
    val editorMode: PdfEditorMode = PdfEditorMode.COMMENT,
    val activeTool: PdfAnnotationTool = PdfAnnotationTool.HIGHLIGHT,
    val activeColorArgb: Int = Color.parseColor("#FFEB3B"),
    val strokeWidthPt: Float = 2.6f,
    val fontSizePt: Float = 14f,
    val annotations: List<PdfEditAnnotation> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val hasSavedSignature: Boolean = false,
    val showSignaturePad: Boolean = false,
    val showCommentsSheet: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val pendingTextTap: PdfNormPoint? = null,
    val pendingNoteTap: PdfNormPoint? = null,
    val activeStickyNote: PdfEditAnnotation.StickyNote? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val currentPageAnnotations: List<PdfEditAnnotation>
        get() = annotations.filter { it.pageIndex == currentPage }
}

@HiltViewModel
class PdfEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfEngine: PdfEngine,
    private val annotationEngine: MuPdfAnnotationEngine,
    private val storageRepository: SafDocumentRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfEditorUiState())
    val uiState: StateFlow<PdfEditorUiState> = _uiState.asStateFlow()

    private var activeHandle: PdfDocumentHandle? = null
    private var sourceLocalPath: String? = null
    private var loadedUri: String? = null

    private val undoStack = mutableListOf<List<PdfEditAnnotation>>()
    private val redoStack = mutableListOf<List<PdfEditAnnotation>>()

    private var savedSignatureStrokes: List<List<PdfNormPoint>> = emptyList()
    private val signatureFile: File by lazy {
        File(context.filesDir, "signatures/default_signature.txt").apply { parentFile?.mkdirs() }
    }

    init {
        loadSavedSignature()
    }

    private fun loadSavedSignature() {
        viewModelScope.launch(ioDispatcher) {
            val loaded = runCatching {
                if (!signatureFile.exists()) return@runCatching emptyList()
                signatureFile.readLines().mapNotNull { line ->
                    val pts = line.split(";").mapNotNull { token ->
                        val parts = token.split(",")
                        if (parts.size == 2) {
                            val x = parts[0].toFloatOrNull()
                            val y = parts[1].toFloatOrNull()
                            if (x != null && y != null) PdfNormPoint(x, y) else null
                        } else null
                    }
                    if (pts.size >= 2) pts else null
                }
            }.getOrDefault(emptyList())
            savedSignatureStrokes = loaded
            _uiState.update { it.copy(hasSavedSignature = loaded.isNotEmpty()) }
        }
    }

    fun saveReusableSignature(strokes: List<List<PdfNormPoint>>) {
        val valid = strokes.filter { it.size >= 2 }
        if (valid.isEmpty()) return
        savedSignatureStrokes = valid
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val serialized = valid.joinToString("\n") { s ->
                    s.joinToString(";") { "${it.x},${it.y}" }
                }
                signatureFile.writeText(serialized)
            }
        }
        _uiState.update {
            it.copy(
                hasSavedSignature = true,
                showSignaturePad = false,
                activeTool = PdfAnnotationTool.SIGNATURE,
                statusMessage = "Signature saved — tap anywhere on the PDF to place it"
            )
        }
    }

    fun openSignaturePad(show: Boolean) {
        _uiState.update { it.copy(showSignaturePad = show) }
    }

    fun openDocument(uriString: String) {
        if (loadedUri == uriString && activeHandle != null) return
        loadedUri = uriString
        closeHandle()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val uri = Uri.parse(uriString)
                val entity = storageRepository.importPickedDocument(uri)
                val localCopy = storageRepository.materializeReadOnlyCopy(uri)
                sourceLocalPath = localCopy.absolutePath

                when (val res = pdfEngine.openDocument(localCopy.absolutePath)) {
                    is PdfOpenResult.Opened -> {
                        activeHandle = res.handle
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                documentTitle = entity.displayName,
                                pageCount = res.metadata.pageCount,
                                currentPage = 0
                            )
                        }
                        renderPage(0)
                    }
                    is PdfOpenResult.PasswordRequired -> {
                        res.handle.close()
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Password-protected PDF editing is not unlocked yet.")
                        }
                    }
                    is PdfOpenResult.Error -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    }
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isLoading = false, errorMessage = t.localizedMessage ?: "Failed to open PDF") }
            }
        }
    }

    private fun renderPage(pageIndex: Int) {
        val handle = activeHandle ?: return
        if (pageIndex !in 0 until handle.pageCount) return
        viewModelScope.launch {
            val (ratio, bmp) = withContext(ioDispatcher) {
                val sz = handle.getPageSize(pageIndex)
                val r = (sz.widthPt / sz.heightPt.coerceAtLeast(1f)).coerceIn(0.45f, 2.2f)
                val b = handle.renderPage(pageIndex, targetWidthPx = 1440, rotationDegrees = 0, invertColors = false)
                r to b
            }
            _uiState.update {
                it.copy(
                    currentPage = pageIndex,
                    currentPageAspectRatio = ratio,
                    currentPageBitmap = bmp
                )
            }
        }
    }

    fun goToPage(index: Int) {
        val maxIdx = (_uiState.value.pageCount - 1).coerceAtLeast(0)
        val clamped = index.coerceIn(0, maxIdx)
        if (clamped == _uiState.value.currentPage && _uiState.value.currentPageBitmap != null) return
        renderPage(clamped)
    }

    fun previousPage() = goToPage(_uiState.value.currentPage - 1)
    fun nextPage() = goToPage(_uiState.value.currentPage + 1)

    // ---- Mode & Tool Controls ----
    fun selectEditorMode(mode: PdfEditorMode) {
        val defaultTool = when (mode) {
            PdfEditorMode.COMMENT -> PdfAnnotationTool.HIGHLIGHT
            PdfEditorMode.FILL_AND_SIGN -> PdfAnnotationTool.FILL_TEXT
        }
        val defaultColor = when (mode) {
            PdfEditorMode.COMMENT -> Color.parseColor("#FFEB3B")
            PdfEditorMode.FILL_AND_SIGN -> Color.parseColor("#111827")
        }
        _uiState.update {
            it.copy(
                editorMode = mode,
                activeTool = defaultTool,
                activeColorArgb = defaultColor
            )
        }
    }

    fun selectTool(tool: PdfAnnotationTool) {
        if (tool == PdfAnnotationTool.SIGNATURE && !_uiState.value.hasSavedSignature) {
            _uiState.update { it.copy(activeTool = tool, showSignaturePad = true) }
            return
        }
        val suggestedColor = when (tool) {
            PdfAnnotationTool.HIGHLIGHT -> Color.parseColor("#FFEB3B")
            PdfAnnotationTool.UNDERLINE -> Color.parseColor("#1E88E5")
            PdfAnnotationTool.STRIKETHROUGH -> Color.parseColor("#E53935")
            PdfAnnotationTool.NOTE -> Color.parseColor("#FFC107")
            PdfAnnotationTool.CHECKMARK -> Color.parseColor("#2E7D32")
            PdfAnnotationTool.CROSS -> Color.parseColor("#D32F2F")
            PdfAnnotationTool.FILL_TEXT, PdfAnnotationTool.SIGNATURE -> Color.parseColor("#111827")
            else -> _uiState.value.activeColorArgb
        }
        _uiState.update { it.copy(activeTool = tool, activeColorArgb = suggestedColor) }
    }

    fun selectColor(argb: Int) {
        _uiState.update { it.copy(activeColorArgb = argb) }
    }

    fun setStrokeWidth(widthPt: Float) {
        _uiState.update { it.copy(strokeWidthPt = widthPt.coerceIn(1f, 12f)) }
    }

    fun setFontSize(sizePt: Float) {
        _uiState.update { it.copy(fontSizePt = sizePt.coerceIn(9f, 28f)) }
    }

    // ---- Annotation Creation ----
    private fun commitAnnotationList(nextList: List<PdfEditAnnotation>) {
        undoStack.add(_uiState.value.annotations)
        redoStack.clear()
        _uiState.update {
            it.copy(
                annotations = nextList,
                canUndo = undoStack.isNotEmpty(),
                canRedo = false,
                hasUnsavedChanges = true
            )
        }
    }

    fun addMarkupDrag(start: PdfNormPoint, end: PdfNormPoint) {
        val s = _uiState.value
        val kind = when (s.activeTool) {
            PdfAnnotationTool.HIGHLIGHT -> PdfMarkupKind.HIGHLIGHT
            PdfAnnotationTool.UNDERLINE -> PdfMarkupKind.UNDERLINE
            PdfAnnotationTool.STRIKETHROUGH -> PdfMarkupKind.STRIKETHROUGH
            else -> return
        }
        val l = kotlin.math.min(start.x, end.x)
        val r = kotlin.math.max(start.x, end.x)
        val t = kotlin.math.min(start.y, end.y)
        val b = kotlin.math.max(start.y, end.y).let { if (it - t < 0.018f) (t + 0.022f).coerceAtMost(1f) else it }
        if (r - l < 0.012f) return
        val item = PdfEditAnnotation.Markup(
            pageIndex = s.currentPage,
            colorArgb = s.activeColorArgb,
            kind = kind,
            leftNorm = l,
            topNorm = t,
            rightNorm = r,
            bottomNorm = b
        )
        commitAnnotationList(s.annotations + item)
    }

    fun addInkStroke(points: List<PdfNormPoint>) {
        if (points.size < 2) return
        val s = _uiState.value
        val item = PdfEditAnnotation.Ink(
            pageIndex = s.currentPage,
            colorArgb = s.activeColorArgb,
            strokeWidthPt = s.strokeWidthPt,
            opacity = 1.0f,
            strokes = listOf(points)
        )
        commitAnnotationList(s.annotations + item)
    }

    fun addShapeDrag(start: PdfNormPoint, end: PdfNormPoint) {
        val s = _uiState.value
        val kind = when (s.activeTool) {
            PdfAnnotationTool.RECTANGLE -> PdfShapeKind.RECTANGLE
            PdfAnnotationTool.CIRCLE -> PdfShapeKind.CIRCLE
            PdfAnnotationTool.LINE -> PdfShapeKind.LINE
            PdfAnnotationTool.ARROW -> PdfShapeKind.ARROW
            else -> return
        }
        if (kotlin.math.hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()) < 0.015) return
        val item = PdfEditAnnotation.Shape(
            pageIndex = s.currentPage,
            colorArgb = s.activeColorArgb,
            kind = kind,
            startXNorm = start.x,
            startYNorm = start.y,
            endXNorm = end.x,
            endYNorm = end.y,
            strokeWidthPt = s.strokeWidthPt
        )
        commitAnnotationList(s.annotations + item)
    }

    fun onCanvasTap(point: PdfNormPoint) {
        val s = _uiState.value
        when (s.activeTool) {
            PdfAnnotationTool.ERASER -> {
                val hit = s.currentPageAnnotations.lastOrNull { PdfAnnotationCoordinateMath.hitTest(it, point.x, point.y) }
                if (hit != null) {
                    commitAnnotationList(s.annotations.filterNot { it.id == hit.id })
                }
            }
            PdfAnnotationTool.NOTE -> {
                val existing = s.currentPageAnnotations
                    .filterIsInstance<PdfEditAnnotation.StickyNote>()
                    .lastOrNull { PdfAnnotationCoordinateMath.hitTest(it, point.x, point.y) }
                if (existing != null) {
                    _uiState.update { it.copy(activeStickyNote = existing) }
                } else {
                    _uiState.update { it.copy(pendingNoteTap = point) }
                }
            }
            PdfAnnotationTool.TEXT, PdfAnnotationTool.FILL_TEXT -> {
                _uiState.update { it.copy(pendingTextTap = point) }
            }
            PdfAnnotationTool.CHECKMARK -> {
                val item = PdfEditAnnotation.FreeText(
                    pageIndex = s.currentPage,
                    colorArgb = s.activeColorArgb,
                    xNorm = (point.x - 0.02f).coerceIn(0f, 0.94f),
                    yNorm = (point.y - 0.02f).coerceIn(0f, 0.94f),
                    widthNorm = 0.06f,
                    heightNorm = 0.045f,
                    text = "✓",
                    fontSizePt = 18f
                )
                commitAnnotationList(s.annotations + item)
            }
            PdfAnnotationTool.CROSS -> {
                val item = PdfEditAnnotation.FreeText(
                    pageIndex = s.currentPage,
                    colorArgb = s.activeColorArgb,
                    xNorm = (point.x - 0.02f).coerceIn(0f, 0.94f),
                    yNorm = (point.y - 0.02f).coerceIn(0f, 0.94f),
                    widthNorm = 0.06f,
                    heightNorm = 0.045f,
                    text = "✕",
                    fontSizePt = 18f
                )
                commitAnnotationList(s.annotations + item)
            }
            PdfAnnotationTool.SIGNATURE -> {
                if (savedSignatureStrokes.isEmpty()) {
                    _uiState.update { it.copy(showSignaturePad = true) }
                } else {
                    placeSavedSignatureAt(point)
                }
            }
            else -> {
                // Allow tapping any sticky note in Scroll mode to read it
                val existing = s.currentPageAnnotations
                    .filterIsInstance<PdfEditAnnotation.StickyNote>()
                    .lastOrNull { PdfAnnotationCoordinateMath.hitTest(it, point.x, point.y) }
                if (existing != null) {
                    _uiState.update { it.copy(activeStickyNote = existing) }
                }
            }
        }
    }

    private fun placeSavedSignatureAt(center: PdfNormPoint) {
        val sigW = 0.28f
        val sigH = 0.09f
        val left = (center.x - sigW / 2f).coerceIn(0.02f, 1f - sigW - 0.02f)
        val top = (center.y - sigH / 2f).coerceIn(0.02f, 1f - sigH - 0.02f)
        val mapped = savedSignatureStrokes.map { stroke ->
            stroke.map { pt -> PdfNormPoint(left + pt.x * sigW, top + pt.y * sigH).clamped() }
        }
        val item = PdfEditAnnotation.Ink(
            pageIndex = _uiState.value.currentPage,
            colorArgb = _uiState.value.activeColorArgb,
            strokeWidthPt = 2.2f,
            opacity = 1f,
            strokes = mapped,
            isSignature = true
        )
        commitAnnotationList(_uiState.value.annotations + item)
    }

    fun confirmAddText(text: String) {
        val pt = _uiState.value.pendingTextTap ?: return
        val clean = text.trim()
        _uiState.update { it.copy(pendingTextTap = null) }
        if (clean.isEmpty()) return
        val s = _uiState.value
        val item = PdfEditAnnotation.FreeText(
            pageIndex = s.currentPage,
            colorArgb = s.activeColorArgb,
            xNorm = pt.x.coerceIn(0.02f, 0.72f),
            yNorm = pt.y.coerceIn(0.02f, 0.92f),
            widthNorm = (clean.length * 0.018f).coerceIn(0.18f, 0.65f),
            heightNorm = 0.048f,
            text = clean,
            fontSizePt = s.fontSizePt
        )
        commitAnnotationList(s.annotations + item)
    }

    fun dismissTextDialog() = _uiState.update { it.copy(pendingTextTap = null) }

    fun confirmAddNote(text: String) {
        val pt = _uiState.value.pendingNoteTap ?: return
        val clean = text.trim()
        _uiState.update { it.copy(pendingNoteTap = null) }
        if (clean.isEmpty()) return
        val s = _uiState.value
        val item = PdfEditAnnotation.StickyNote(
            pageIndex = s.currentPage,
            colorArgb = s.activeColorArgb,
            xNorm = pt.x,
            yNorm = pt.y,
            text = clean
        )
        commitAnnotationList(s.annotations + item)
    }

    fun dismissNoteDialog() = _uiState.update { it.copy(pendingNoteTap = null, activeStickyNote = null) }

    fun deleteAnnotationById(id: String) {
        commitAnnotationList(_uiState.value.annotations.filterNot { it.id == id })
        _uiState.update { it.copy(activeStickyNote = null) }
    }

    fun openCommentsSheet(show: Boolean) = _uiState.update { it.copy(showCommentsSheet = show) }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(_uiState.value.annotations)
        val prev = undoStack.removeAt(undoStack.lastIndex)
        _uiState.update {
            it.copy(
                annotations = prev,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                hasUnsavedChanges = prev.isNotEmpty()
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(_uiState.value.annotations)
        val next = redoStack.removeAt(redoStack.lastIndex)
        _uiState.update {
            it.copy(
                annotations = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                hasUnsavedChanges = true
            )
        }
    }

    // ---- Save Annotated Copy (Never Overwrites Original!) ----
    fun suggestedSaveFileName(): String {
        val base = _uiState.value.documentTitle.removeSuffix(".pdf").ifBlank { "Document" }
        return "${base}_Annotated.pdf"
    }

    fun saveAnnotatedCopyToUri(destinationUri: Uri) {
        val srcPath = sourceLocalPath ?: return
        val annots = _uiState.value.annotations
        if (annots.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Add at least one annotation before saving.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val tempOut = File(context.cacheDir, "annotated_${System.currentTimeMillis()}.pdf")
                val res = annotationEngine.saveAnnotatedCopy(srcPath, tempOut.absolutePath, annots)
                if (res.isSuccess && tempOut.exists()) {
                    val copied = withContext(ioDispatcher) {
                        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                            FileInputStream(tempOut).use { input -> input.copyTo(out) }
                            out.flush()
                            true
                        } ?: false
                    }
                    runCatching { tempOut.delete() }
                    if (copied) {
                        runCatching {
                            storageRepository.registerScannedPdf(
                                uri = destinationUri,
                                fallbackName = suggestedSaveFileName(),
                                pageCount = _uiState.value.pageCount
                            )
                        }
                        _uiState.update {
                            it.copy(
                                hasUnsavedChanges = false,
                                statusMessage = "Saved PDF copy with ${annots.size} annotation(s)"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(errorMessage = "Could not write PDF to selected location.") }
                    }
                } else {
                    _uiState.update {
                        it.copy(errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to save annotations.")
                    }
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(errorMessage = t.localizedMessage ?: "Save failed.") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun requestExit(onBack: () -> Unit) {
        if (_uiState.value.hasUnsavedChanges && _uiState.value.annotations.isNotEmpty()) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            onBack()
        }
    }

    fun dismissDiscardDialog() = _uiState.update { it.copy(showDiscardDialog = false) }
    fun clearMessages() = _uiState.update { it.copy(statusMessage = null, errorMessage = null) }

    private fun closeHandle() {
        runCatching { activeHandle?.close() }
        activeHandle = null
    }

    override fun onCleared() {
        closeHandle()
        super.onCleared()
    }
}
