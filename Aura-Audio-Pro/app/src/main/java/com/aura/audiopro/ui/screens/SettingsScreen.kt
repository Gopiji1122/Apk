package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.aura.audiopro.audio.EqualizerController
import com.aura.audiopro.core.AlbumArtCache
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.model.ThemeMode
import com.aura.audiopro.ui.theme.AuraAccentColors
import com.aura.audiopro.ui.viewmodel.AuraViewModel

@Composable
fun SettingsScreen(
    vm: AuraViewModel,
    accent: Color,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeedDialog: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val playbackSpeed by vm.playbackSpeed.collectAsState()
    val sleepSec by EqualizerController.sleepTimerRemainingSec.collectAsState()

    var showLicenseDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }

            // 1. APPEARANCE & THEME (Auto System Default + Manual Override)
            SettingsCategoryCard(title = "1. Theme & Appearance", accent = accent) {
                Text("Theme Mode (Default: Auto Device Theme)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeMode.entries.toList()) { mode ->
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
                    subtitle = "Match player background to current song artwork",
                    checked = settings.dynamicArtworkColor,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(dynamicArtworkColor = v) } }
                )
                SettingsSwitchRow(
                    title = "Rotating Vinyl Now Playing Mode",
                    subtitle = "Show spinning vinyl platter on the Audio Player screen",
                    checked = settings.vinylMode,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(vinylMode = v) } }
                )
            }

            // 2. HOME SCREEN CUSTOMIZATION (Optional sections OFF by default)
            val hs = settings.homeSections
            SettingsCategoryCard(title = "2. Home Screen Customization", accent = accent) {
                Text(
                    "Optional sections are off by default to keep Home minimal.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SettingsSwitchRow(
                    title = "Recently Played Section",
                    subtitle = "Show recently played carousel on Home",
                    checked = hs.showRecentlyPlayed,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showRecentlyPlayed = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Favorites Section",
                    subtitle = "Show liked tracks carousel on Home",
                    checked = hs.showFavorites,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showFavorites = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Currently Playing Banner",
                    subtitle = "Show large active track card on Home",
                    checked = hs.showCurrentlyPlaying,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showCurrentlyPlaying = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Library Statistics Card",
                    subtitle = "Show total tracks, artists, albums, and duration on Home",
                    checked = hs.showStats,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showStats = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Recently Added Section",
                    subtitle = "Show newly added audio files on Home",
                    checked = hs.showRecentlyAdded,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showRecentlyAdded = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Most Played Section",
                    subtitle = "Show top played tracks on Home",
                    checked = hs.showMostPlayed,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showMostPlayed = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Playlists Row",
                    subtitle = "Show custom playlists carousel on Home",
                    checked = hs.showPlaylists,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showPlaylists = v)) }
                    }
                )
            }

            // 3. EQUALIZER, SLEEP TIMER & PLAYBACK
            SettingsCategoryCard(title = "3. Equalizer, Sleep Timer & Playback", accent = accent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEqualizer() }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Equalizer & Audio FX", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("5-band equalizer, presets, bass boost & virtualizer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSleepTimer() }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Sleep Timer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (sleepSec > 0) "Active (${LyricsAndMetadataHelper.formatDuration(sleepSec * 1000L)} remaining)" else "Off — Set auto-stop timer",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSpeedDialog() }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Playback Speed", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Current: ${playbackSpeed}x", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }

                SettingsSwitchRow(
                    title = "Gapless Playback",
                    subtitle = "Seamless transitions between consecutive album tracks",
                    checked = settings.gaplessPlayback,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(gaplessPlayback = v) } }
                )
            }

            // 4. LIBRARY, MOTION & STORAGE
            SettingsCategoryCard(title = "4. Library Filter, Motion & Storage", accent = accent) {
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
                SettingsSwitchRow(
                    title = "Reduced Motion Mode",
                    subtitle = "Disable continuous animations to save battery",
                    checked = settings.reducedMotion,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(reducedMotion = v) } }
                )
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

            // 5. BACKUP, PRIVACY & ABOUT
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
                        Text("100% Offline • Zero INTERNET Permission • No Ads", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Reset theme, playback, and minimal Home defaults", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = Color(0xFFF43F5E))
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Aura Audio Pro v1.1.0 • Pull down on any list to refresh library",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
