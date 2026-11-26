package com.offlinepdf.studio.feature.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.database.BookmarkEntity
import com.offlinepdf.studio.core.database.DocumentDao
import com.offlinepdf.studio.core.pdf.PdfDocumentHandle
import com.offlinepdf.studio.core.pdf.PdfEngine
import com.offlinepdf.studio.core.pdf.PdfGeometryHelper
import com.offlinepdf.studio.core.pdf.PdfMetadata
import com.offlinepdf.studio.core.pdf.PdfOpenResult
import com.offlinepdf.studio.core.pdf.PdfOutlineEntry
import com.offlinepdf.studio.core.pdf.PdfSearchBox
import com.offlinepdf.studio.core.pdf.PdfSearchHit
import com.offlinepdf.studio.core.pdf.ReadingCanvasTheme
import com.offlinepdf.studio.core.pdf.ReadingLayoutMode
import com.offlinepdf.studio.core.pdf.ZoomPreset
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReaderUiState(
    val isLoading: Boolean = true,
    val documentTitle: String = "",
    val pageCount: Int = 0,
    val initialResumePage: Int = 0,
    val currentPage: Int = 0,
    val rotationDegrees: Int = 0,
    val layoutMode: ReadingLayoutMode = ReadingLayoutMode.CONTINUOUS_VERTICAL,
    val zoomPreset: ZoomPreset = ZoomPreset.FIT_WIDTH,
    val canvasTheme: ReadingCanvasTheme = ReadingCanvasTheme.LIGHT,
    val controlsVisible: Boolean = true,
    val needsPassword: Boolean = false,
    val passwordError: String? = null,
    val errorMessage: String? = null,
    val metadata: PdfMetadata = PdfMetadata(),
    val outline: List<PdfOutlineEntry> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val searchQuery: String = "",
    val searchHits: List<PdfSearchHit> = emptyList(),
    val activeHitIndex: Int = -1,
    val isSearching: Boolean = false,
    val extractedPageText: String? = null,
    val isExtractingText: Boolean = false
) {
    val bookmarkedPages: Set<Int>
        get() = bookmarks.map { it.pageIndex }.toSet()

    val searchBoxesByPage: Map<Int, List<PdfSearchBox>>
        get() = searchHits.associate { it.pageIndex to it.boxes }
}

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    private val storageRepository: SafDocumentRepository,
    private val documentDao: DocumentDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var activeHandle: PdfDocumentHandle? = null
    private var activeUriString: String? = null
    private var searchJob: Job? = null

    fun openUri(uriString: String) {
        if (activeUriString == uriString && activeHandle != null) return
        activeUriString = uriString
        closeHandle()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, needsPassword = false) }
            try {
                val uri = Uri.parse(uriString)
                val entity = storageRepository.importPickedDocument(uri)
                val localCopy = storageRepository.materializeReadOnlyCopy(uri)

                observeBookmarks(uriString)

                when (val result = pdfEngine.openDocument(localCopy.absolutePath)) {
                    is PdfOpenResult.Opened -> {
                        activeHandle = result.handle
                        val resumePage = entity.lastReadPage.coerceIn(
                            0,
                            (result.metadata.pageCount - 1).coerceAtLeast(0)
                        )
                        val outline = withContext(ioDispatcher) { result.handle.getOutline() }
                        storageRepository.updatePageCountAndProgress(
                            uri = uriString,
                            pageCount = result.metadata.pageCount,
                            lastReadPage = resumePage
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                documentTitle = entity.displayName,
                                pageCount = result.metadata.pageCount,
                                initialResumePage = resumePage,
                                currentPage = resumePage,
                                metadata = result.metadata,
                                outline = outline
                            )
                        }
                    }
                    is PdfOpenResult.PasswordRequired -> {
                        activeHandle = result.handle
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                documentTitle = entity.displayName,
                                needsPassword = true
                            )
                        }
                    }
                    is PdfOpenResult.Error -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = result.message)
                        }
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.localizedMessage ?: "Could not open PDF file."
                    )
                }
            }
        }
    }

    fun submitPassword(password: String) {
        val handle = activeHandle ?: return
        val uri = activeUriString ?: return
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { handle.authenticate(password) }
            if (ok) {
                val meta = withContext(ioDispatcher) { handle.getMetadata() }
                val outline = withContext(ioDispatcher) { handle.getOutline() }
                storageRepository.updatePageCountAndProgress(uri, meta.pageCount, 0)
                _uiState.update {
                    it.copy(
                        needsPassword = false,
                        passwordError = null,
                        pageCount = meta.pageCount,
                        metadata = meta,
                        outline = outline
                    )
                }
            } else {
                _uiState.update { it.copy(passwordError = "Incorrect password. Please try again.") }
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val handle = activeHandle ?: return null
        val state = _uiState.value
        return withContext(ioDispatcher) {
            runCatching {
                handle.renderPage(
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx,
                    rotationDegrees = state.rotationDegrees,
                    invertColors = state.canvasTheme == ReadingCanvasTheme.NIGHT_INVERTED
                )
            }.getOrNull()
        }
    }

    suspend fun renderThumbnail(pageIndex: Int): Bitmap? {
        val handle = activeHandle ?: return null
        return withContext(ioDispatcher) {
            runCatching { handle.renderThumbnail(pageIndex, 220) }.getOrNull()
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val uri = activeUriString ?: return
        val total = _uiState.value.pageCount
        if (pageIndex < 0 || pageIndex >= total) return
        if (_uiState.value.currentPage == pageIndex) return
        _uiState.update { it.copy(currentPage = pageIndex) }
        viewModelScope.launch {
            storageRepository.updatePageCountAndProgress(uri, total, pageIndex)
        }
    }

    fun toggleControlsVisibility() {
        _uiState.update { it.copy(controlsVisible = !it.controlsVisible) }
    }

    fun setLayoutMode(mode: ReadingLayoutMode) {
        _uiState.update { it.copy(layoutMode = mode) }
    }

    fun setZoomPreset(preset: ZoomPreset) {
        _uiState.update { it.copy(zoomPreset = preset) }
    }

    fun cycleCanvasTheme() {
        _uiState.update {
            val next = when (it.canvasTheme) {
                ReadingCanvasTheme.LIGHT -> ReadingCanvasTheme.DARK_CANVAS
                ReadingCanvasTheme.DARK_CANVAS -> ReadingCanvasTheme.NIGHT_INVERTED
                ReadingCanvasTheme.NIGHT_INVERTED -> ReadingCanvasTheme.LIGHT
            }
            it.copy(canvasTheme = next)
        }
    }

    fun rotateClockwise() {
        _uiState.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun toggleCurrentPageBookmark() {
        val uri = activeUriString ?: return
        val page = _uiState.value.currentPage
        viewModelScope.launch {
            if (_uiState.value.bookmarkedPages.contains(page)) {
                documentDao.deleteBookmark(uri, page)
            } else {
                documentDao.upsertBookmark(
                    BookmarkEntity(
                        documentUri = uri,
                        pageIndex = page,
                        label = "Page ${page + 1}",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteBookmark(pageIndex: Int) {
        val uri = activeUriString ?: return
        viewModelScope.launch {
            documentDao.deleteBookmark(uri, pageIndex)
        }
    }

    fun searchText(query: String) {
        val handle = activeHandle ?: return
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchHits = emptyList(), activeHitIndex = -1, isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            val hits = withContext(ioDispatcher) {
                val results = mutableListOf<PdfSearchHit>()
                for (i in 0 until handle.pageCount) {
                    handle.searchPage(i, query)?.let { results.add(it) }
                }
                results
            }
            _uiState.update {
                it.copy(
                    searchHits = hits,
                    activeHitIndex = if (hits.isNotEmpty()) 0 else -1,
                    isSearching = false
                )
            }
        }
    }

    fun selectNextHit(): Int? {
        val hits = _uiState.value.searchHits
        if (hits.isEmpty()) return null
        val next = PdfGeometryHelper.nextHitIndex(_uiState.value.activeHitIndex, hits.size)
        _uiState.update { it.copy(activeHitIndex = next) }
        return hits[next].pageIndex
    }

    fun selectPreviousHit(): Int? {
        val hits = _uiState.value.searchHits
        if (hits.isEmpty()) return null
        val prev = PdfGeometryHelper.previousHitIndex(_uiState.value.activeHitIndex, hits.size)
        _uiState.update { it.copy(activeHitIndex = prev) }
        return hits[prev].pageIndex
    }

    fun loadCurrentPageText() {
        val handle = activeHandle ?: return
        val page = _uiState.value.currentPage
        viewModelScope.launch {
            _uiState.update { it.copy(isExtractingText = true, extractedPageText = null) }
            val text = withContext(ioDispatcher) { handle.extractPageText(page) }
            _uiState.update {
                it.copy(
                    isExtractingText = false,
                    extractedPageText = text.ifBlank {
                        "No selectable text layer found on page ${page + 1}. This page may be a scanned image (offline OCR arrives in Phase 4)."
                    }
                )
            }
        }
    }

    fun dismissExtractedText() {
        _uiState.update { it.copy(extractedPageText = null, isExtractingText = false) }
    }

    private fun observeBookmarks(uriString: String) {
        viewModelScope.launch {
            documentDao.observeBookmarks(uriString).collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
    }

    private fun closeHandle() {
        searchJob?.cancel()
        runCatching { activeHandle?.close() }
        activeHandle = null
    }

    override fun onCleared() {
        closeHandle()
        super.onCleared()
    }
}
