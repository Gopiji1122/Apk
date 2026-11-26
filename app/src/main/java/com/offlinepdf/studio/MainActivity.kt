package com.offlinepdf.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.database.UserPreferencesRepository
import com.offlinepdf.studio.core.designsystem.theme.OfflinePdfStudioTheme
import com.offlinepdf.studio.core.designsystem.theme.ThemeMode
import com.offlinepdf.studio.navigation.StudioNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialPdfUri = extractPdfUri(intent)

        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = com.offlinepdf.studio.core.database.UserPreferences()
            )
            val themeMode = when (prefs.themeMode) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            OfflinePdfStudioTheme(themeMode = themeMode) {
                StudioNavHost(initialDocumentUri = initialPdfUri?.toString())
            }
        }
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent?.action == Intent.ACTION_VIEW) {
            return intent.data
        }
        return null
    }
}
