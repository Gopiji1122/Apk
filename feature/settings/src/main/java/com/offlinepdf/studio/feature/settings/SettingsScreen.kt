package com.offlinepdf.studio.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.common.Formatters

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLicensesDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // Theme Mode
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (key, label) ->
                            FilterChip(
                                selected = state.preferences.themeMode == key,
                                onClick = { viewModel.setThemeMode(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Language Selection (English & Hindi)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Language / भाषा", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.preferences.languageTag == "en",
                            onClick = { viewModel.setLanguage("en") },
                            label = { Text("English") }
                        )
                        FilterChip(
                            selected = state.preferences.languageTag == "hi",
                            onClick = { viewModel.setLanguage("hi") },
                            label = { Text("हिन्दी") }
                        )
                    }
                }
            }
        }

        // Interaction toggles
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptic Feedback", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = state.preferences.hapticFeedback,
                            onCheckedChange = viewModel::setHapticFeedback
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Smooth Animations", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = state.preferences.animationsEnabled,
                            onCheckedChange = viewModel::setAnimationsEnabled
                        )
                    }
                }
            }
        }

        // Cache & Storage
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Storage & Temporary Cache", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Working PDF cache: ${Formatters.formatFileSize(state.cacheSizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = viewModel::clearCache) {
                        Text("Clear Temporary Files")
                    }
                    state.lastClearedMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Privacy & Open-Source Licenses
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Privacy & Open-Source Licenses", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "100% offline. No internet permission, no telemetry, no cloud sync, no ads.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showLicensesDialog = true }) {
                        Text("View Licenses & MuPDF AGPL Notice")
                    }
                }
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-Source Licenses") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• MuPDF (com.artifex.mupdf:fitz:1.28.5): GNU AGPL v3.0 or later. Private personal offline use is permitted without commercial license. If ever distributed, full application source must be released under AGPL v3+.")
                    Text("• AndroidX Jetpack, Compose, Room, DataStore, Navigation, Hilt: Apache License 2.0.")
                    Text("• Kotlin & Coroutines: Apache License 2.0.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) { Text("Close") }
            }
        )
    }
}
