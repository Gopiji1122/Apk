package com.offlinepdf.studio.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.database.DocumentEntity
import com.offlinepdf.studio.core.pdf.DocumentThumbnailProvider
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val continueReading: DocumentEntity? = null,
    val recentDocuments: List<DocumentEntity> = emptyList(),
    val favoriteDocuments: List<DocumentEntity> = emptyList(),
    val scannedDocuments: List<DocumentEntity> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SafDocumentRepository,
    private val thumbnails: DocumentThumbnailProvider
) : ViewModel() {

    suspend fun thumbnail(uriString: String) = thumbnails.thumbnail(uriString)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRecentDocuments(10),
        repository.observeFavorites(),
        repository.observeScannedDocuments()
    ) { recent, favorites, scanned ->
        HomeUiState(
            continueReading = recent.firstOrNull(),
            recentDocuments = recent,
            favoriteDocuments = favorites,
            scannedDocuments = scanned
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun onDocumentPicked(uri: Uri, onImported: (String) -> Unit) {
        viewModelScope.launch {
            val entity = repository.importPickedDocument(uri)
            onImported(entity.uriString)
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(doc.uriString, !doc.isFavorite)
        }
    }
}
