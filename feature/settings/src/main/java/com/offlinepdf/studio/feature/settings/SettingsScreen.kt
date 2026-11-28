package com.offlinepdf.studio.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepdf.studio.core.common.Formatters
import com.offlinepdf.studio.core.designsystem.R
import com.offlinepdf.studio.core.designsystem.components.ScreenHeader
import com.offlinepdf.studio.core.designsystem.components.SegmentedChoice
import com.offlinepdf.studio.core.designsystem.components.StudioCard
import com.offlinepdf.studio.core.designsystem.components.staggeredEntrance

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val prefs = state.preferences

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader(stringResource(R.string.settings_title)) }

        item {
            SettingsCard(Icons.Outlined.Palette, stringResource(R.string.settings_appearance), 0) {
                SegmentedChoice(
                    options = listOf(
                        "SYSTEM" to stringResource(R.string.theme_system),
                        "LIGHT" to stringResource(R.string.theme_light),
                        "DARK" to stringResource(R.string.theme_dark)
                    ),
                    selected = prefs.themeMode,
                    onSelect = viewModel::setThemeMode
                )
            }
        }

        item {
            SettingsCard(Icons.Outlined.Language, "${stringResource(R.string.settings_language)} / भाषा", 1) {
                SegmentedChoice(
                    options = listOf("en" to "English", "hi" to "हिन्दी"),
                    selected = prefs.languageTag,
                    onSelect = viewModel::setLanguage
                )
            }
        }

        item {
            SettingsCard(Icons.Outlined.AutoStories, stringResource(R.string.settings_reading), 2) {
                Text(
                    stringResource(R.string.settings_default_layout),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SegmentedChoice(
                    options = listOf(
                        "CONTINUOUS_VERTICAL" to stringResource(R.string.layout_scroll),
                        "SINGLE_PAGE_HORIZONTAL" to stringResource(R.string.layout_single),
                        "TWO_PAGE_SPREAD" to stringResource(R.string.layout_spread)
                    ),
                    selected = prefs.defaultPageMode,
                    onSelect = viewModel::setDefaultPageMode
                )
                ToggleRow(
                    icon = Icons.Outlined.DarkMode,
                    title = stringResource(R.string.settings_night_reading),
                    subtitle = stringResource(R.string.settings_night_reading_sub),
                    checked = prefs.nightReadingMode,
                    onChange = viewModel::setNightReadingMode
                )
            }
        }

        item {
            SettingsCard(Icons.Outlined.Tune, stringResource(R.string.settings_interaction), 3) {
                ToggleRow(
                    title = stringResource(R.string.settings_haptics),
                    subtitle = stringResource(R.string.settings_haptics_sub),
                    checked = prefs.hapticFeedback,
                    onChange = viewModel::setHapticFeedback
                )
                ToggleRow(
                    title = stringResource(R.string.settings_animations),
                    subtitle = stringResource(R.string.settings_animations_sub),
                    checked = prefs.animationsEnabled,
                    onChange = viewModel::setAnimationsEnabled
                )
            }
        }

        item {
            SettingsCard(Icons.Outlined.Storage, stringResource(R.string.settings_storage), 4) {
                Text(
                    stringResource(R.string.settings_cache_size, Formatters.formatFileSize(state.cacheSizeBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = viewModel::clearCache, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.settings_clear_cache))
                }
                if (state.lastClearedMessage != null) {
                    Text(
                        stringResource(R.string.settings_cache_cleared),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            SettingsCard(Icons.Outlined.Lock, stringResource(R.string.settings_privacy), 5) {
                PrivacyLine(stringResource(R.string.privacy_no_internet))
                PrivacyLine(stringResource(R.string.privacy_no_tracking))
                PrivacyLine(stringResource(R.string.privacy_local_only))
            }
        }

        item {
            SettingsCard(Icons.Outlined.Info, stringResource(R.string.settings_about), 6) {
                Text(
                    "${stringResource(R.string.app_title)} • $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showLicenses = true }) {
                    Text(stringResource(R.string.settings_licenses))
                }
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.license_mupdf))
                    Text(stringResource(R.string.license_opencv))
                    Text(stringResource(R.string.license_androidx))
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    index: Int,
    content: @Composable () -> Unit
) {
    StudioCard(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .staggeredEntrance(index)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
