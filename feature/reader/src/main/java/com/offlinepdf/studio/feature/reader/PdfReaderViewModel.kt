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
import com.offlinepdf.studio.core.pdf.PdfSearchBox
import com.offlinepdf.studio.core.pdf.PdfSearchHit
import com.offlinepdf.studio.core.pdf.ReadingCanvasTheme
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val initialResumePage: Int = 0,
    val currentPage: Int = 0,
    val canvasTheme: ReadingCanvasTheme = ReadingCanvasTheme.DARK_CANVAS,
    val controlsVisible: Boolean = true,
    val needsPassword: Boolean = false,
    val passwordError: String? = null,
    val errorMessage: String? = null,
    val statusToast: String? = null,
    val metadata: PdfMetadata = PdfMetadata(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val searchQuery: String = "",
    val searchHits: List<PdfSearchHit> = emptyList(),
    val activeHitIndex: Int = -1,
    val isSearching: Boolean = false
) {
    val bookmarkedPages: Set<Int>
        get() = bookmarks.map { it.pageIndex }.toSet()

    val searchBoxesByPage: Map<Int, List<PdfSearchBox>>
        get() = searchHits.associate { it.pageIndex to it.boxes }

    val totalMatchCount: Int
        get() = searchHits.sumOf { it.boxes.size }
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
                        storageRepository.updatePageCountAndProgress(
                            uri = uriString,
                            pageCount = result.metadata.pageCount,
                            lastReadPage = resumePage
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                documentTitle = entity.displayName,
                                fileSizeBytes = entity.sizeBytes.takeIf { s -> s > 0L } ?: localCopy.length(),
                                pageCount = result.metadata.pageCount,
                                initialResumePage = resumePage,
                                currentPage = resumePage,
                                metadata = result.metadata
                            )
                        }
                    }
                    is PdfOpenResult.PasswordRequired -> {
                        activeHandle = result.handle
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                documentTitle = entity.displayName,
                                fileSizeBytes = entity.sizeBytes,
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
                storageRepository.updatePageCountAndProgress(uri, meta.pageCount, 0)
                _uiState.update {
                    it.copy(
                        needsPassword = false,
                        passwordError = null,
                        pageCount = meta.pageCount,
                        metadata = meta
                    )
                }
            } else {
                _uiState.update { it.copy(passwordError = "Incorrect password. Please try again.") }
            }
        }
    }

    suspend fun getPageAspectRatio(pageIndex: Int): Float {
        val handle = activeHandle ?: return 0.707f
        return withContext(ioDispatcher) {
            runCatching { handle.getPageSize(pageIndex).aspectRatio.coerceIn(0.4f, 2.2f) }
                .getOrDefault(0.707f)
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
                    rotationDegrees = 0,
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

    fun setControlsVisible(visible: Boolean) {
        if (_uiState.value.controlsVisible != visible) {
            _uiState.update { it.copy(controlsVisible = visible) }
        }
    }

    fun cycleCanvasTheme() {
        _uiState.update {
            val next = when (it.canvasTheme) {
                ReadingCanvasTheme.DARK_CANVAS -> ReadingCanvasTheme.LIGHT
                ReadingCanvasTheme.LIGHT -> ReadingCanvasTheme.NIGHT_INVERTED
                ReadingCanvasTheme.NIGHT_INVERTED -> ReadingCanvasTheme.DARK_CANVAS
            }
            it.copy(canvasTheme = next)
        }
    }

    fun toggleCurrentPageBookmark() {
        val uri = activeUriString ?: return
        val page = _uiState.value.currentPage
        viewModelScope.launch {
            if (_uiState.value.bookmarkedPages.contains(page)) {
                documentDao.deleteBookmark(uri, page)
                _uiState.update { it.copy(statusToast = "Bookmark removed (Page ${page + 1})") }
            } else {
                documentDao.upsertBookmark(
                    BookmarkEntity(
                        documentUri = uri,
                        pageIndex = page,
                        label = "Page ${page + 1}",
                        createdAt = System.currentTimeMillis()
                    )
                )
                _uiState.update { it.copy(statusToast = "Bookmarked Page ${page + 1}") }
            }
        }
    }

    fun deleteBookmark(pageIndex: Int) {
        val uri = activeUriString ?: return
        viewModelScope.launch { documentDao.deleteBookmark(uri, pageIndex) }
    }

    fun exportToDestination(destUri: Uri) {
        val src = activeUriString ?: return
        viewModelScope.launch {
            val ok = storageRepository.exportPdfToUri(src, destUri)
            _uiState.update {
                it.copy(statusToast = if (ok) "PDF saved to storage" else "Failed to save PDF")
            }
        }
    }

    fun clearStatusToast() {
        _uiState.update { it.copy(statusToast = null) }
    }

    fun searchText(query: String, onFirstMatchPage: (Int) -> Unit = {}) {
        val handle = activeHandle ?: return
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchHits = emptyList(), activeHitIndex = -1, isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(220)
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
            hits.firstOrNull()?.let { onFirstMatchPage(it.pageIndex) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(searchQuery = "", searchHits = emptyList(), activeHitIndex = -1, isSearching = false)
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
