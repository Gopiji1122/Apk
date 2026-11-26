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
import com.offlinepdf.studio.core.pdf.PdfMetadata
import com.offlinepdf.studio.core.pdf.PdfOpenResult
import com.offlinepdf.studio.core.pdf.PdfSearchHit
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
    val currentPage: Int = 0,
    val rotationDegrees: Int = 0,
    val nightMode: Boolean = false,
    val needsPassword: Boolean = false,
    val passwordError: String? = null,
    val errorMessage: String? = null,
    val metadata: PdfMetadata = PdfMetadata(),
    val bookmarkedPages: Set<Int> = emptySet(),
    val searchQuery: String = "",
    val searchHits: List<PdfSearchHit> = emptyList(),
    val isSearching: Boolean = false
)

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
                        storageRepository.updatePageCountAndProgress(
                            uri = uriString,
                            pageCount = result.metadata.pageCount,
                            lastReadPage = entity.lastReadPage.coerceIn(0, (result.metadata.pageCount - 1).coerceAtLeast(0))
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                documentTitle = entity.displayName,
                                pageCount = result.metadata.pageCount,
                                currentPage = entity.lastReadPage.coerceIn(0, (result.metadata.pageCount - 1).coerceAtLeast(0)),
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

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val handle = activeHandle ?: return null
        val state = _uiState.value
        return withContext(ioDispatcher) {
            runCatching {
                handle.renderPage(
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx,
                    rotationDegrees = state.rotationDegrees,
                    invertColors = state.nightMode
                )
            }.getOrNull()
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val uri = activeUriString ?: return
        val total = _uiState.value.pageCount
        if (pageIndex < 0 || pageIndex >= total) return
        _uiState.update { it.copy(currentPage = pageIndex) }
        viewModelScope.launch {
            storageRepository.updatePageCountAndProgress(uri, total, pageIndex)
        }
    }

    fun rotateClockwise() {
        _uiState.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun toggleNightMode() {
        _uiState.update { it.copy(nightMode = !it.nightMode) }
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

    fun searchText(query: String) {
        val handle = activeHandle ?: return
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchHits = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            val hits = withContext(ioDispatcher) {
                val results = mutableListOf<PdfSearchHit>()
                for (i in 0 until handle.pageCount) {
                    handle.searchPage(i, query)?.let { results.add(it) }
                }
                results
            }
            _uiState.update { it.copy(searchHits = hits, isSearching = false) }
        }
    }

    private fun observeBookmarks(uriString: String) {
        viewModelScope.launch {
            documentDao.observeBookmarks(uriString).collect { list ->
                _uiState.update { it.copy(bookmarkedPages = list.map { b -> b.pageIndex }.toSet()) }
            }
        }
    }

    private fun closeHandle() {
        runCatching { activeHandle?.close() }
        activeHandle = null
    }

    override fun onCleared() {
        closeHandle()
        super.onCleared()
    }
}
