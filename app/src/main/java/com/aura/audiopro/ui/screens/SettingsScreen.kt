package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.audiopro.core.AlbumArtCache
import com.aura.audiopro.model.ThemeMode
import com.aura.audiopro.ui.theme.AuraAccentColors
import com.aura.audiopro.ui.viewmodel.AuraViewModel

@Composable
fun SettingsScreen(
    vm: AuraViewModel,
    accent: Color,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeedDialog: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val allSongs by vm.allSongs.collectAsState()
    val excludedFolders by vm.excludedFolders.collectAsState()
    val playbackSpeed by vm.playbackSpeed.collectAsState()

    var showLicenseDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

        // 1. APPEARANCE
        SettingsCategoryCard(title = "1. Appearance & Personalization", accent = accent) {
            Text("Theme Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                        label = { Text(mode.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Accent Color", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AuraAccentColors.forEachIndexed { idx, col ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(
                                width = if (settings.accentIndex == idx) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                                shape = CircleShape
                            )
                            .clickable { vm.updateSettings { it.copy(accentIndex = idx) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (settings.accentIndex == idx) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsSwitchRow(
                title = "Dynamic Artwork Backdrop Color",
                subtitle = "Extract dominant color from current album art",
                checked = settings.dynamicArtworkColor,
                onCheckedChange = { v -> vm.updateSettings { it.copy(dynamicArtworkColor = v) } }
            )
            SettingsSwitchRow(
                title = "Rotating Vinyl Now Playing Mode",
                subtitle = "Display spinning vinyl platter on Now Playing screen",
                checked = settings.vinylMode,
                onCheckedChange = { v -> vm.updateSettings { it.copy(vinylMode = v) } }
            )
        }

        // 2. AUDIO & PLAYBACK
        SettingsCategoryCard(title = "2. Audio & Playback", accent = accent) {
            SettingsSwitchRow(
                title = "Gapless Playback",
                subtitle = "Seamless transitions between consecutive album tracks",
                checked = settings.gaplessPlayback,
                onCheckedChange = { v -> vm.updateSettings { it.copy(gaplessPlayback = v) } }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSpeedDialog() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Playback Speed", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Current speed: ${playbackSpeed}x", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSleepTimer() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Sleep Timer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Configure automatic playback stop timer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }

        // 3. LIBRARY & STORAGE
        SettingsCategoryCard(title = "3. Library, Storage & Notifications", accent = accent) {
            Text(
                "Minimum Track Duration Filter: ${settings.minAudioDurationSec}s",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Slider(
                value = settings.minAudioDurationSec.toFloat(),
                onValueChange = { v ->
                    vm.updateSettings { it.copy(minAudioDurationSec = v.toInt()) }
                },
                valueRange = 0f..60f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.rescanLibrary() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Rescan Local MediaStore", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${allSongs.size} tracks indexed • ${excludedFolders.size} folders excluded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = accent)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AlbumArtCache.clear() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Clear Artwork Memory Cache", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Free cached album art bitmaps in RAM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = accent)
            }
        }

        // 4. ANIMATIONS
        SettingsCategoryCard(title = "4. Motion & Battery Optimization", accent = accent) {
            SettingsSwitchRow(
                title = "Reduced Motion Mode",
                subtitle = "Disable continuous vinyl spin and visualizer animations to save battery",
                checked = settings.reducedMotion,
                onCheckedChange = { v -> vm.updateSettings { it.copy(reducedMotion = v) } }
            )
        }

        // 5. BACKUP, RESTORE & ABOUT
        SettingsCategoryCard(title = "5. Backup, Privacy & About", accent = accent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBackupDialog = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Export Playlists Backup (JSON)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Save your local playlists structure offline", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Backup, contentDescription = null, tint = accent)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLicenseDialog = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Privacy Policy & Open-Source Licenses", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("100% Offline • Zero INTERNET Permission • No Ads or Tracking", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = accent)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.resetSettingsToDefaults() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Restore Default Settings", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFFF43F5E))
                    Text("Reset appearance, playback, and home layout preferences", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = Color(0xFFF43F5E))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Aura Audio Pro v1.0.0 • AndroidX Media3 ExoPlayer • Room SQLite",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("Privacy & Open-Source Licenses") },
            text = {
                Text(
                    "Privacy Guarantee:\n" +
                        "• Aura Audio Pro does not request the android.permission.INTERNET permission.\n" +
                        "• Zero analytics, zero telemetry, zero ads, and zero cloud uploads.\n\n" +
                        "Open-Source Libraries (Apache 2.0):\n" +
                        "• AndroidX Media3 ExoPlayer & MediaSession\n" +
                        "• Jetpack Compose & Material 3\n" +
                        "• AndroidX Room SQLite Database\n" +
                        "• Kotlin Coroutines & Flow"
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text("Close") }
            }
        )
    }

    if (showBackupDialog) {
        val json = remember { vm.exportPlaylistsJson() }
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Offline Playlists JSON Backup") },
            text = {
                OutlinedTextField(
                    value = json,
                    onValueChange = {},
                    readOnly = true,
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
