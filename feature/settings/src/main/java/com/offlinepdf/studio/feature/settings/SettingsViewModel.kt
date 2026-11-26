package com.offlinepdf.studio.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepdf.studio.core.database.UserPreferences
import com.offlinepdf.studio.core.database.UserPreferencesRepository
import com.offlinepdf.studio.core.storage.SafDocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val cacheSizeBytes: Long = 0L,
    val lastClearedMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val storageRepository: SafDocumentRepository
) : ViewModel() {

    private val cacheBytesFlow = MutableStateFlow(0L)
    private val statusMessageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsRepository.preferences,
        cacheBytesFlow,
        statusMessageFlow
    ) { prefs, cacheBytes, status ->
        SettingsUiState(prefs, cacheBytes, status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            cacheBytesFlow.value = storageRepository.getWorkingCacheSizeBytes()
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefsRepository.setThemeMode(mode) }
    }

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            prefsRepository.setLanguageTag(tag)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setHapticFeedback(enabled) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setAnimationsEnabled(enabled) }
    }

    fun clearCache() {
        viewModelScope.launch {
            storageRepository.clearWorkingCache()
            cacheBytesFlow.value = storageRepository.getWorkingCacheSizeBytes()
            statusMessageFlow.value = "Temporary PDF cache cleared."
        }
    }
}
