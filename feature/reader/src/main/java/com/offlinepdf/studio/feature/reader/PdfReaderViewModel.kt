package com.offlinepdf.studio.feature.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.common.IoDispatcher
import com.offlinepdf.studio.core.database.BookmarkEntity
import com.offlinepdf.studio.core.database.DocumentDao
import com.offlinepdf.studio.core.database.UserPreferencesRepository
import com.offlinepdf.studio.core.pdf.PdfDocumentHandle
import com.offlinepdf.studio.core.pdf.PdfEngine
import com.offlinepdf.studio.core.pdf.PdfLink
import com.offlinepdf.studio.core.pdf.PdfMetadata
import com.offlinepdf.studio.core.pdf.PdfOpenResult
import com.offlinepdf.studio.core.pdf.PdfOutlineEntry
import com.offlinepdf.studio.core.pdf.PdfRegionTile
import com.offlinepdf.studio.core.pdf.PdfSearchBox
import com.offlinepdf.studio.core.pdf.ReadingCanvasTheme
import com.offlinepdf.studio.core.pdf.ReadingLayoutMode
import com.offlinepdf.studio.core.pdf.ZoomPreset
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class ReaderMessage {
    BOOKMARK_ADDED, BOOKMARK_REMOVED, SAVED, SAVE_FAILED, TEXT_COPIED, NO_TEXT, SHARE_FAILED
}

data class ReaderToast(val message: ReaderMessage, val page: Int = 0)

data class SearchMatch(val pageIndex: Int, val box: PdfSearchBox)

data class HighlightBox(val box: PdfSearchBox, val matchIndex: Int)

data class ReaderUiState(
    val isLoading: Boolean = true,
    val documentTitle: String = "",
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val initialResumePage: Int = 0,
    val currentPage: Int = 0,
    val canvasTheme: ReadingCanvasTheme = ReadingCanvasTheme.DARK_CANVAS,
    val layoutMode: ReadingLayoutMode = ReadingLayoutMode.CONTINUOUS_VERTICAL,
    val zoomPreset: ZoomPreset = ZoomPreset.FIT_WIDTH,
    val controlsVisible: Boolean = true,
    val needsPassword: Boolean = false,
    val passwordError: Boolean = false,
    val errorMessage: String? = null,
    val toast: ReaderToast? = null,
    val metadata: PdfMetadata = PdfMetadata(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val outline: List<PdfOutlineEntry> = emptyList(),
    /** width / height per page; filled progressively after open. */
    val pageAspects: List<Float> = emptyList(),
    val searchQuery: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val matchesByPage: Map<Int, List<HighlightBox>> = emptyMap(),
    val activeMatchIndex: Int = -1,
    val isSearching: Boolean = false,
    val searchProgress: Float = 0f
) {
    val bookmarkedPages: Set<Int> get() = bookmarks.map { it.pageIndex }.toSet()

    fun aspectOf(page: Int): Float = pageAspects.getOrNull(page) ?: 0.707f
}

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    private val pdfEngine: PdfEngine,
    private val storageRepository: SafDocumentRepository,
    private val documentDao: DocumentDao,
    private val prefsRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var activeHandle: PdfDocumentHandle? = null
    private var activeUriString: String? = null
    private var searchJob: Job? = null
    private var prefetchJob: Job? = null
    private var bookmarkJob: Job? = null
    private var lastScrollPage: Int = 0
    private val linkCache = ConcurrentHashMap<Int, List<PdfLink>>()

    fun openUri(uriString: String) {
        if (activeUriString == uriString && activeHandle != null) return
        activeUriString = uriString
        closeHandle()
        linkCache.clear()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, needsPassword = false) }
            try {
                val prefs = prefsRepository.preferences.first()
                val layout = runCatching { ReadingLayoutMode.valueOf(prefs.defaultPageMode) }
                    .getOrDefault(ReadingLayoutMode.CONTINUOUS_VERTICAL)
                val theme = if (prefs.nightReadingMode) ReadingCanvasTheme.NIGHT_INVERTED else ReadingCanvasTheme.DARK_CANVAS
                _uiState.update { it.copy(layoutMode = layout, canvasTheme = theme) }

                val uri = Uri.parse(uriString)
                // One copy only: materialise first, then register in the library without re-copying.
                val localCopy = storageRepository.materializeReadOnlyCopy(uri)
                val entity = storageRepository.importPickedDocument(uri, materializeCopy = false)
                observeBookmarks(uriString)

                when (val result = pdfEngine.openDocument(localCopy.absolutePath)) {
                    is PdfOpenResult.Opened -> {
                        activeHandle = result.handle
                        finishOpening(result.handle, result.metadata, entity.displayName,
                            entity.sizeBytes.takeIf { it > 0L } ?: localCopy.length(), entity.lastReadPage)
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
                        _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.localizedMessage ?: "Could not open PDF file.")
                }
            }
        }
    }

    private suspend fun finishOpening(
        handle: PdfDocumentHandle,
        meta: PdfMetadata,
        title: String,
        size: Long,
        savedPage: Int
    ) {
        val uri = activeUriString ?: return
        val count = meta.pageCount
        val resume = savedPage.coerceIn(0, (count - 1).coerceAtLeast(0))
        storageRepository.updatePageCountAndProgress(uri, count, resume)
        val firstAspect = withContext(ioDispatcher) {
            runCatching { handle.getPageSize(0).aspectRatio }.getOrDefault(0.707f)
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                documentTitle = title,
                fileSizeBytes = size,
                pageCount = count,
                initialResumePage = resume,
                currentPage = resume,
                metadata = meta,
                pageAspects = List(count) { firstAspect }
            )
        }
        loadPageAspects(handle, count, firstAspect)
        viewModelScope.launch(ioDispatcher) {
            val outline = runCatching { handle.getOutline() }.getOrDefault(emptyList())
            _uiState.update { it.copy(outline = outline) }
        }
    }

    /** Loads true page sizes in the background so mixed-size documents never jump while scrolling. */
    private fun loadPageAspects(handle: PdfDocumentHandle, count: Int, fallback: Float) {
        viewModelScope.launch(ioDispatcher) {
            val aspects = FloatArray(count) { fallback }
            var changed = false
            for (i in 0 until count) {
                ensureActive()
                val a = runCatching { handle.getPageSize(i).aspectRatio }.getOrDefault(fallback)
                if (a != aspects[i]) { aspects[i] = a; changed = true }
                if (changed && (i % 64 == 63 || i == count - 1)) {
                    val snapshot = aspects.toList()
                    _uiState.update { it.copy(pageAspects = snapshot) }
                    changed = false
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
                _uiState.update { it.copy(needsPassword = false, passwordError = false) }
                val saved = documentDao.getByUri(uri)?.lastReadPage ?: 0
                finishOpening(handle, meta, _uiState.value.documentTitle, _uiState.value.fileSizeBytes, saved)
            } else {
                _uiState.update { it.copy(passwordError = true) }
            }
        }
    }

    // ───────────── Rendering ─────────────

    private fun invert() = _uiState.value.canvasTheme == ReadingCanvasTheme.NIGHT_INVERTED

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val handle = activeHandle ?: return null
        val invert = invert()
        return withContext(ioDispatcher) {
            runCatching { handle.renderPage(pageIndex, targetWidthPx, 0, invert) }.getOrNull()
        }
    }

    suspend fun renderThumbnail(pageIndex: Int): Bitmap? {
        val handle = activeHandle ?: return null
        return withContext(ioDispatcher) {
            runCatching { handle.renderThumbnail(pageIndex, 220) }.getOrNull()
        }
    }

    suspend fun renderTile(
        pageIndex: Int,
        l: Float, t: Float, r: Float, b: Float,
        fullPageWidthPx: Int
    ): PdfRegionTile? {
        val handle = activeHandle ?: return null
        val invert = invert()
        return withContext(ioDispatcher) {
            runCatching { handle.renderRegion(pageIndex, l, t, r, b, fullPageWidthPx, invert) }.getOrNull()
        }
    }

    suspend fun loadLinks(pageIndex: Int): List<PdfLink> {
        linkCache[pageIndex]?.let { return it }
        val handle = activeHandle ?: return emptyList()
        val links = withContext(ioDispatcher) {
            runCatching { handle.getPageLinks(pageIndex) }.getOrDefault(emptyList())
        }
        linkCache[pageIndex] = links
        return links
    }

    /** Directional pre-render: next 2 pages in the scroll direction + 1 behind. */
    fun prefetch(currentPage: Int, widthPx: Int) {
        val handle = activeHandle ?: return
        val total = _uiState.value.pageCount
        if (total <= 1) return
        val forward = currentPage >= lastScrollPage
        lastScrollPage = currentPage
        val targets = (if (forward) listOf(currentPage + 1, currentPage + 2, currentPage - 1)
        else listOf(currentPage - 1, currentPage - 2, currentPage + 1)).filter { it in 0 until total }
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(ioDispatcher) {
            val invert = invert()
            for (idx in targets) {
                ensureActive()
                runCatching { handle.renderPage(idx, widthPx, 0, invert) }
            }
        }
    }

    suspend fun extractPageText(pageIndex: Int): String {
        val handle = activeHandle ?: return ""
        return withContext(ioDispatcher) { runCatching { handle.extractPageText(pageIndex) }.getOrDefault("") }
    }

    // ───────────── State changes ─────────────

    fun onPageChanged(pageIndex: Int) {
        val uri = activeUriString ?: return
        val total = _uiState.value.pageCount
        if (pageIndex < 0 || pageIndex >= total || _uiState.value.currentPage == pageIndex) return
        _uiState.update { it.copy(currentPage = pageIndex) }
        viewModelScope.launch { storageRepository.updatePageCountAndProgress(uri, total, pageIndex) }
    }

    fun toggleControlsVisibility() = _uiState.update { it.copy(controlsVisible = !it.controlsVisible) }

    fun setControlsVisible(visible: Boolean) {
        if (_uiState.value.controlsVisible != visible) _uiState.update { it.copy(controlsVisible = visible) }
    }

    fun setCanvasTheme(theme: ReadingCanvasTheme) = _uiState.update { it.copy(canvasTheme = theme) }

    fun setLayoutMode(mode: ReadingLayoutMode) {
        _uiState.update { it.copy(layoutMode = mode) }
        viewModelScope.launch { prefsRepository.setDefaultPageMode(mode.name) }
    }

    fun setZoomPreset(preset: ZoomPreset) = _uiState.update { it.copy(zoomPreset = preset) }

    fun toggleCurrentPageBookmark() {
        val uri = activeUriString ?: return
        val page = _uiState.value.currentPage
        viewModelScope.launch {
            if (_uiState.value.bookmarkedPages.contains(page)) {
                documentDao.deleteBookmark(uri, page)
                _uiState.update { it.copy(toast = ReaderToast(ReaderMessage.BOOKMARK_REMOVED, page + 1)) }
            } else {
                documentDao.upsertBookmark(
                    BookmarkEntity(uri, page, "Page ${page + 1}", System.currentTimeMillis())
                )
                _uiState.update { it.copy(toast = ReaderToast(ReaderMessage.BOOKMARK_ADDED, page + 1)) }
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
            postToast(if (ok) ReaderMessage.SAVED else ReaderMessage.SAVE_FAILED)
        }
    }

    suspend fun prepareShareUri(): Uri? {
        val src = activeUriString ?: return null
        return runCatching {
            storageRepository.prepareShareUri(src, _uiState.value.documentTitle)
        }.getOrElse {
            postToast(ReaderMessage.SHARE_FAILED)
            null
        }
    }

    fun postToast(message: ReaderMessage, page: Int = 0) =
        _uiState.update { it.copy(toast = ReaderToast(message, page)) }

    fun clearToast() = _uiState.update { it.copy(toast = null) }

    // ───────────── Search ─────────────

    fun searchText(query: String, onFirstMatchPage: (Int) -> Unit = {}) {
        val handle = activeHandle ?: return
        searchJob?.cancel()
        prefetchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.clearedSearch(query) }
            return
        }
        _uiState.update { it.copy(searchQuery = query, isSearching = true, searchProgress = 0f) }
        searchJob = viewModelScope.launch {
            delay(220)
            val total = handle.pageCount
            val found = ArrayList<SearchMatch>()
            var announcedFirst = false
            withContext(ioDispatcher) {
                for (i in 0 until total) {
                    ensureActive()
                    handle.searchPage(i, query)?.boxes?.forEach { found.add(SearchMatch(i, it)) }
                    if (i % 12 == 11 || i == total - 1) {
                        val snapshot = found.toList()
                        _uiState.update {
                            it.withMatches(snapshot, keepActive = true)
                                .copy(searchProgress = (i + 1f) / total)
                        }
                        if (!announcedFirst && snapshot.isNotEmpty()) {
                            announcedFirst = true
                            val first = snapshot.first().pageIndex
                            withContext(kotlinx.coroutines.Dispatchers.Main) { onFirstMatchPage(first) }
                        }
                    }
                }
            }
            _uiState.update { it.copy(isSearching = false, searchProgress = 1f) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.clearedSearch("") }
    }

    fun selectNextMatch(): Int? = stepMatch(+1)
    fun selectPreviousMatch(): Int? = stepMatch(-1)

    private fun stepMatch(direction: Int): Int? {
        val matches = _uiState.value.matches
        if (matches.isEmpty()) return null
        val cur = _uiState.value.activeMatchIndex
        val next = ((if (cur < 0) 0 else cur + direction) + matches.size) % matches.size
        _uiState.update { it.copy(activeMatchIndex = next) }
        return matches[next].pageIndex
    }

    private fun ReaderUiState.clearedSearch(query: String) = copy(
        searchQuery = query, matches = emptyList(), matchesByPage = emptyMap(),
        activeMatchIndex = -1, isSearching = false, searchProgress = 0f
    )

    private fun ReaderUiState.withMatches(list: List<SearchMatch>, keepActive: Boolean): ReaderUiState {
        val byPage = HashMap<Int, MutableList<HighlightBox>>()
        list.forEachIndexed { idx, m -> byPage.getOrPut(m.pageIndex) { mutableListOf() }.add(HighlightBox(m.box, idx)) }
        val active = when {
            list.isEmpty() -> -1
            keepActive && activeMatchIndex in list.indices -> activeMatchIndex
            else -> 0
        }
        return copy(matches = list, matchesByPage = byPage, activeMatchIndex = active)
    }

    private fun observeBookmarks(uriString: String) {
        bookmarkJob?.cancel()
        bookmarkJob = viewModelScope.launch {
            documentDao.observeBookmarks(uriString).collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
    }

    private fun closeHandle() {
        searchJob?.cancel()
        prefetchJob?.cancel()
        runCatching { activeHandle?.close() }
        activeHandle = null
    }

    override fun onCleared() {
        closeHandle()
        super.onCleared()
    }
}
