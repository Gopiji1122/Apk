package com.offlinepdf.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.database.UserPreferences
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

    private var externalPdfUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        externalPdfUri = extractPdfUri(intent)?.toString()

        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val themeMode = when (prefs.themeMode) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            OfflinePdfStudioTheme(themeMode = themeMode) {
                StudioNavHost(initialDocumentUri = externalPdfUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPdfUri(intent)?.toString()?.let { uriStr ->
            externalPdfUri = uriStr
        }
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent?.action == Intent.ACTION_VIEW) {
            return intent.data
        }
        return null
    }
}
