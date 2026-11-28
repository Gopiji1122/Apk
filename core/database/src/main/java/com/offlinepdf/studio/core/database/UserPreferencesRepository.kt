package com.offlinepdf.studio.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val animationsEnabled: Boolean = true
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
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM",
            languageTag = prefs[Keys.LANGUAGE_TAG] ?: "en",
            defaultPageMode = prefs[Keys.DEFAULT_PAGE_MODE] ?: "CONTINUOUS_VERTICAL",
            nightReadingMode = prefs[Keys.NIGHT_READING_MODE] ?: false,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.userPreferencesDataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setLanguageTag(tag: String) {
        context.userPreferencesDataStore.edit { it[Keys.LANGUAGE_TAG] = tag }
    }

    suspend fun setDefaultPageMode(mode: String) {
        context.userPreferencesDataStore.edit { it[Keys.DEFAULT_PAGE_MODE] = mode }
    }

    suspend fun setNightReadingMode(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.NIGHT_READING_MODE] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.ANIMATIONS_ENABLED] = enabled }
    }
}
