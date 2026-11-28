package com.offlinepdf.studio.feature.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.database.DocumentEntity
import com.offlinepdf.studio.core.pdf.DocumentThumbnailProvider
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FileSortOrder { DATE_DESC, NAME_ASC, SIZE_DESC }

enum class FileFilter { ALL, FAVORITES, SCANNED }

data class FilesUiState(
    val query: String = "",
    val sortOrder: FileSortOrder = FileSortOrder.DATE_DESC,
    val isGridView: Boolean = false,
    val filter: FileFilter = FileFilter.ALL,
    val documents: List<DocumentEntity> = emptyList()
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: SafDocumentRepository,
    private val thumbnails: DocumentThumbnailProvider
) : ViewModel() {

    suspend fun thumbnail(uriString: String) = thumbnails.thumbnail(uriString)

    suspend fun shareUri(doc: DocumentEntity): Uri =
        repository.prepareShareUri(doc.uriString, doc.displayName)

    private val queryFlow = MutableStateFlow("")
    private val sortFlow = MutableStateFlow(FileSortOrder.DATE_DESC)
    private val gridFlow = MutableStateFlow(false)
    private val filterFlow = MutableStateFlow(FileFilter.ALL)

    val uiState: StateFlow<FilesUiState> = combine(
        repository.observeAllDocuments(),
        queryFlow,
        sortFlow,
        gridFlow,
        filterFlow
    ) { docs, query, sort, isGrid, filter ->
        val filtered = docs.filter { doc ->
            val matchesFav = when (filter) {
                FileFilter.ALL -> true
                FileFilter.FAVORITES -> doc.isFavorite
                FileFilter.SCANNED -> doc.isScanned
            }
            val matchesQuery = query.isBlank() || doc.displayName.contains(query, ignoreCase = true)
            matchesFav && matchesQuery
        }
        val sorted = when (sort) {
            FileSortOrder.DATE_DESC -> filtered.sortedByDescending { it.lastOpenedAt }
            FileSortOrder.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            FileSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }
        FilesUiState(
            query = query,
            sortOrder = sort,
            isGridView = isGrid,
            filter = filter,
            documents = sorted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesUiState())

    fun updateQuery(value: String) { queryFlow.value = value }
    fun updateSort(order: FileSortOrder) { sortFlow.value = order }
    fun toggleGridView() { gridFlow.value = !gridFlow.value }
    fun updateFilter(filter: FileFilter) { filterFlow.value = filter }

    fun importDocuments(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { repository.importPickedDocument(it) }
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch { repository.toggleFavorite(doc.uriString, !doc.isFavorite) }
    }

    fun rename(uri: String, newName: String) {
        viewModelScope.launch { repository.renameInLibrary(uri, newName) }
    }

    fun removeFromLibrary(uri: String) {
        viewModelScope.launch { repository.removeFromLibrary(uri) }
    }
}
