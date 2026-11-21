package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.audiopro.audio.EqualizerController
import com.aura.audiopro.core.AlbumArtCache
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.model.ThemeMode
import com.aura.audiopro.ui.components.ProIconBox
import com.aura.audiopro.ui.components.ProPill
import com.aura.audiopro.ui.components.bounceClick
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bounceClick(scaleDown = 0.88f, onClick = onBack)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(12.dp))
                Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }

            // 1. APPEARANCE
            ProGroupTitle("APPEARANCE", accent)
            ProSettingsCard {
                Text(
                    "Theme Mode (Default: Auto Device Theme)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeMode.entries.toList()) { mode ->
                        ProPill(
                            text = mode.label,
                            selected = settings.themeMode == mode,
                            onClick = { vm.updateSettings { it.copy(themeMode = mode) } }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Accent colour",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuraAccentColors.forEachIndexed { idx, col ->
                        val selected = settings.accentIndex == idx
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .bounceClick(scaleDown = 0.88f) {
                                    vm.updateSettings { it.copy(accentIndex = idx) }
                                }
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = if (selected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingsSwitchRow(
                    title = "Dynamic Artwork Backdrop",
                    subtitle = "Tint FullPlayer gradient with album art dominant color",
                    checked = settings.dynamicArtworkColor,
                    accent = accent,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(dynamicArtworkColor = v) } }
                )
                SettingsSwitchRow(
                    title = "Rotating Vinyl Platter Mode",
                    subtitle = "Spinning vinyl disc on Now Playing screen",
                    checked = settings.vinylMode,
                    accent = accent,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(vinylMode = v) } }
                )
            }

            // 2. HOME SCREEN CUSTOMIZATION
            val hs = settings.homeSections
            ProGroupTitle("HOME SCREEN SECTIONS", accent)
            ProSettingsCard {
                Text(
                    "Optional sections are off by default to keep Home minimal.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                SettingsSwitchRow(
                    title = "Recently Played Carousel",
                    subtitle = "Show recently played songs on Home",
                    checked = hs.showRecentlyPlayed,
                    accent = accent,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showRecentlyPlayed = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Liked Songs Carousel",
                    subtitle = "Show favorite tracks carousel on Home",
                    checked = hs.showFavorites,
                    accent = accent,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showFavorites = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Currently Playing Banner",
                    subtitle = "Show active track banner on Home",
                    checked = hs.showCurrentlyPlaying,
                    accent = accent,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showCurrentlyPlaying = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Library Statistics Card",
                    subtitle = "Show total tracks, artists, albums & duration on Home",
                    checked = hs.showStats,
                    accent = accent,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showStats = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Recently Added Carousel",
                    subtitle = "Show newly added audio files on Home",
                    checked = hs.showRecentlyAdded,
                    accent = accent,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showRecentlyAdded = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Most Played Carousel",
                    subtitle = "Show top played tracks on Home",
                    checked = hs.showMostPlayed,
                    accent = accent,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showMostPlayed = v)) }
                    }
                )
                SettingsSwitchRow(
                    title = "Playlists Row",
                    subtitle = "Show custom playlists carousel on Home",
                    checked = hs.showPlaylists,
                    accent = accent,
                    onCheckedChange = { v ->
                        vm.updateSettings { it.copy(homeSections = it.homeSections.copy(showPlaylists = v)) }
                    }
                )
            }

            // 3. AUDIO & PLAYBACK
            ProGroupTitle("AUDIO & PLAYBACK", accent)
            ProSettingsCard {
                ProSettingActionRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Equalizer & Stereo Balance",
                    subtitle = "5-band equalizer, bass boost, virtualizer & L/R balance",
                    accent = accent,
                    onClick = onOpenEqualizer
                )
                ProSettingActionRow(
                    icon = Icons.Rounded.Bedtime,
                    title = "Sleep Timer",
                    subtitle = if (sleepSec > 0) "Active (${LyricsAndMetadataHelper.formatDuration(sleepSec * 1000L)} left)" else "Off — Tap to set auto-stop timer",
                    accent = accent,
                    onClick = onOpenSleepTimer
                )
                ProSettingActionRow(
                    icon = Icons.Rounded.Speed,
                    title = "Playback Speed",
                    subtitle = "Current: ${playbackSpeed}x",
                    accent = accent,
                    onClick = onOpenSpeedDialog
                )
                SettingsSwitchRow(
                    title = "Gapless Playback",
                    subtitle = "Seamless transitions between consecutive album tracks",
                    checked = settings.gaplessPlayback,
                    accent = accent,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(gaplessPlayback = v) } }
                )
            }

            // 4. LIBRARY & STORAGE
            ProGroupTitle("LIBRARY & PERFORMANCE", accent)
            ProSettingsCard {
                Text(
                    "Ignore audio clips shorter than: ${settings.minAudioDurationSec}s",
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
                    title = "Reduced Motion",
                    subtitle = "Disable continuous animations to save battery",
                    checked = settings.reducedMotion,
                    accent = accent,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(reducedMotion = v) } }
                )
                ProSettingActionRow(
                    icon = Icons.Rounded.CleaningServices,
                    title = "Clear Artwork Memory Cache",
                    subtitle = "Free cached album art bitmaps in RAM",
                    accent = accent,
                    onClick = { AlbumArtCache.clear() }
                )
            }

            // 5. BACKUP & ABOUT
            ProGroupTitle("BACKUP & ABOUT", accent)
            ProSettingsCard {
                ProSettingActionRow(
                    icon = Icons.Rounded.Backup,
                    title = "Export Playlists Backup (JSON)",
                    subtitle = "Save your local playlists structure offline",
                    accent = accent,
                    onClick = { showBackupDialog = true }
                )
                ProSettingActionRow(
                    icon = Icons.Rounded.VerifiedUser,
                    title = "Privacy & Open-Source Licenses",
                    subtitle = "100% offline • No ads, no tracking, no internet permission",
                    accent = accent,
                    onClick = { showLicenseDialog = true }
                )
                ProSettingActionRow(
                    icon = Icons.Rounded.RestartAlt,
                    title = "Restore Default Settings",
                    subtitle = "Reset theme, playback, and minimal Home defaults",
                    accent = Color(0xFFFF5C9D),
                    onClick = { vm.resetSettingsToDefaults() }
                )
            }

            Text(
                text = "Aura Audio Pro 2.0\n100% offline. No ads, no tracking, no internet permission.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
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
private fun ProGroupTitle(text: String, accent: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = accent,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
    )
}

@Composable
private fun ProSettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun ProSettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProIconBox(icon = icon, tint = accent, boxSize = 40.dp, cornerRadius = 11.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accent)
        )
    }
}
