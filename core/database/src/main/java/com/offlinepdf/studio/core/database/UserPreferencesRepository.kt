package com.offlinepdf.studio.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "offline_pdf_studio_prefs"
)

data class UserPreferences(
    val themeMode: String = "SYSTEM",
    val languageTag: String = "en",
    val defaultPageMode: String = "CONTINUOUS_VERTICAL",
    val nightReadingMode: Boolean = false,
    val hapticFeedback: Boolean = true,
    val animationsEnabled: Boolean = true,
    // Scanner settings (persisted locally via DataStore)
    val scannerAutoCapture: Boolean = true,
    val scannerCaptureSound: Boolean = true,
    val scannerDefaultFlash: Boolean = false,
    val scannerBatchMode: Boolean = false,
    val scannerDefaultFilter: String = "AUTO_COLOR",
    val scannerDefaultExportFormat: String = "PDF",
    val scannerDefaultPdfQuality: Int = 88,
    val scannerOcrLanguage: String = "eng+hin",
    val scannerDetectionSensitivity: String = "NORMAL" // LOW, NORMAL, HIGH
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val DEFAULT_PAGE_MODE = stringPreferencesKey("default_page_mode")
        val NIGHT_READING_MODE = booleanPreferencesKey("night_reading_mode")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")

        val SCANNER_AUTO_CAPTURE = booleanPreferencesKey("scanner_auto_capture")
        val SCANNER_CAPTURE_SOUND = booleanPreferencesKey("scanner_capture_sound")
        val SCANNER_DEFAULT_FLASH = booleanPreferencesKey("scanner_default_flash")
        val SCANNER_BATCH_MODE = booleanPreferencesKey("scanner_batch_mode")
        val SCANNER_DEFAULT_FILTER = stringPreferencesKey("scanner_default_filter")
        val SCANNER_DEFAULT_EXPORT = stringPreferencesKey("scanner_default_export")
        val SCANNER_DEFAULT_QUALITY = intPreferencesKey("scanner_default_quality")
        val SCANNER_OCR_LANG = stringPreferencesKey("scanner_ocr_lang")
        val SCANNER_SENSITIVITY = stringPreferencesKey("scanner_sensitivity")
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM",
            languageTag = prefs[Keys.LANGUAGE_TAG] ?: "en",
            defaultPageMode = prefs[Keys.DEFAULT_PAGE_MODE] ?: "CONTINUOUS_VERTICAL",
            nightReadingMode = prefs[Keys.NIGHT_READING_MODE] ?: false,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
            scannerAutoCapture = prefs[Keys.SCANNER_AUTO_CAPTURE] ?: true,
            scannerCaptureSound = prefs[Keys.SCANNER_CAPTURE_SOUND] ?: true,
            scannerDefaultFlash = prefs[Keys.SCANNER_DEFAULT_FLASH] ?: false,
            scannerBatchMode = prefs[Keys.SCANNER_BATCH_MODE] ?: false,
            scannerDefaultFilter = prefs[Keys.SCANNER_DEFAULT_FILTER] ?: "AUTO_COLOR",
            scannerDefaultExportFormat = prefs[Keys.SCANNER_DEFAULT_EXPORT] ?: "PDF",
            scannerDefaultPdfQuality = prefs[Keys.SCANNER_DEFAULT_QUALITY] ?: 88,
            scannerOcrLanguage = prefs[Keys.SCANNER_OCR_LANG] ?: "eng+hin",
            scannerDetectionSensitivity = prefs[Keys.SCANNER_SENSITIVITY] ?: "NORMAL"
        )
    }

    suspend fun setThemeMode(mode: String) { context.userPreferencesDataStore.edit { it[Keys.THEME_MODE] = mode } }
    suspend fun setLanguageTag(tag: String) { context.userPreferencesDataStore.edit { it[Keys.LANGUAGE_TAG] = tag } }
    suspend fun setDefaultPageMode(mode: String) { context.userPreferencesDataStore.edit { it[Keys.DEFAULT_PAGE_MODE] = mode } }
    suspend fun setNightReadingMode(enabled: Boolean) { context.userPreferencesDataStore.edit { it[Keys.NIGHT_READING_MODE] = enabled } }
    suspend fun setHapticFeedback(enabled: Boolean) { context.userPreferencesDataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled } }
    suspend fun setAnimationsEnabled(enabled: Boolean) { context.userPreferencesDataStore.edit { it[Keys.ANIMATIONS_ENABLED] = enabled } }

    suspend fun setScannerAutoCapture(enabled: Boolean) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_AUTO_CAPTURE] = enabled } }
    suspend fun setScannerCaptureSound(enabled: Boolean) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_CAPTURE_SOUND] = enabled } }
    suspend fun setScannerDefaultFlash(enabled: Boolean) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_DEFAULT_FLASH] = enabled } }
    suspend fun setScannerBatchMode(enabled: Boolean) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_BATCH_MODE] = enabled } }
    suspend fun setScannerDefaultFilter(filter: String) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_DEFAULT_FILTER] = filter } }
    suspend fun setScannerDefaultExportFormat(fmt: String) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_DEFAULT_EXPORT] = fmt } }
    suspend fun setScannerDefaultPdfQuality(q: Int) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_DEFAULT_QUALITY] = q.coerceIn(40, 100) } }
    suspend fun setScannerOcrLanguage(lang: String) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_OCR_LANG] = lang } }
    suspend fun setScannerDetectionSensitivity(s: String) { context.userPreferencesDataStore.edit { it[Keys.SCANNER_SENSITIVITY] = s } }
}
