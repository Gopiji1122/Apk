package com.aura.audiopro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.audiopro.audio.EqualizerController
import com.aura.audiopro.model.MainDestination
import com.aura.audiopro.model.PlaylistModel
import com.aura.audiopro.model.Song
import com.aura.audiopro.ui.components.MiniPlayerBar
import com.aura.audiopro.ui.components.SongListItem
import com.aura.audiopro.ui.screens.*
import com.aura.audiopro.ui.theme.AuraAccentColors
import com.aura.audiopro.ui.theme.AuraAudioProTheme
import com.aura.audiopro.ui.viewmodel.AuraViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AuraViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val accent = AuraAccentColors.getOrElse(settings.accentIndex) { AuraAccentColors[0] }

            AuraAudioProTheme(
                themeMode = settings.themeMode,
                accentIndex = settings.accentIndex
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuraMainShell(vm = vm, accent = accent)
                }
            }
        }
    }
}

data class SongListDetailState(
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val playlistId: Long? = null
)

@Composable
fun AuraMainShell(vm: AuraViewModel, accent: Color) {
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    var requestedNotification by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            vm.rescanLibrary()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requestedNotification) {
                requestedNotification = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            vm.rescanLibrary()
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    val currentSong by vm.currentSong.collectAsState()
    val isMiniPlayerVisible by vm.isMiniPlayerVisible.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val currentPosition by vm.currentPosition.collectAsState()
    val totalDuration by vm.totalDuration.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val playbackSpeed by vm.playbackSpeed.collectAsState()
    val settings by vm.settings.collectAsState()

    var activeTab by remember { mutableStateOf(MainDestination.HOME) }
    var detailOverlay by remember { mutableStateOf<SongListDetailState?>(null) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showOutputDeviceDialog by remember { mutableStateOf(false) }
    var songsForPlaylistPicker by remember { mutableStateOf<List<Long>?>(null) }
    var songForMetadataEditor by remember { mutableStateOf<Song?>(null) }

    // Complete layered back-navigation handler so Back never exits the app prematurely
    val shouldInterceptBack = showEqualizer ||
        showSettings ||
        showNowPlaying ||
        detailOverlay != null ||
        activeTab != MainDestination.HOME

    BackHandler(enabled = shouldInterceptBack) {
        when {
            showEqualizer -> showEqualizer = false
            showSettings -> showSettings = false
            showNowPlaying -> showNowPlaying = false
            detailOverlay != null -> detailOverlay = null
            activeTab != MainDestination.HOME -> activeTab = MainDestination.HOME
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    AnimatedVisibility(
                        visible = currentSong != null && isMiniPlayerVisible && !showNowPlaying,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        currentSong?.let { song ->
                            MiniPlayerBar(
                                song = song,
                                isPlaying = isPlaying,
                                currentPosition = currentPosition,
                                totalDuration = totalDuration,
                                accent = accent,
                                onExpand = { showNowPlaying = true },
                                onPlayPause = { vm.togglePlayPause() },
                                onNext = { vm.skipNext() },
                                onDismiss = { vm.dismissMiniPlayer() }
                            )
                        }
                    }

                    // 4-Option Footer: 1. Home, 2. Library, 3. Playlist, 4. Folder
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        MainDestination.entries.forEach { dest ->
                            val icon = when (dest) {
                                MainDestination.HOME -> Icons.Rounded.Home
                                MainDestination.LIBRARY -> Icons.Rounded.LibraryMusic
                                MainDestination.PLAYLISTS -> Icons.Rounded.QueueMusic
                                MainDestination.FOLDERS -> Icons.Rounded.Folder
                            }
                            NavigationBarItem(
                                selected = activeTab == dest && !showSettings && !showEqualizer,
                                onClick = {
                                    activeTab = dest
                                    detailOverlay = null
                                    showSettings = false
                                    showEqualizer = false
                                },
                                icon = { Icon(icon, contentDescription = dest.label) },
                                label = { Text(dest.label, fontSize = 11.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    indicatorColor = accent.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!hasAudioPermission) {
                    PermissionOnboardingView(
                        accent = accent,
                        onGrant = { permissionLauncher.launch(audioPermission) },
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    )
                } else {
                    when (activeTab) {
                        MainDestination.HOME -> HomeScreen(
                            vm = vm,
                            accent = accent,
                            onOpenNowPlaying = { showNowPlaying = true },
                            onOpenSettings = { showSettings = true },
                            onNavigateTab = { activeTab = it },
                            onOpenSongListDetail = { t, sub, s, pid ->
                                detailOverlay = SongListDetailState(t, sub, s, pid)
                            }
                        )
                        MainDestination.LIBRARY -> LibraryScreen(
                            vm = vm,
                            accent = accent,
                            onOpenSettings = { showSettings = true },
                            onOpenPlaylistPickerForSongs = { songsForPlaylistPicker = it },
                            onOpenMetadataEditor = { songForMetadataEditor = it },
                            onOpenSongListDetail = { t, sub, s, pid ->
                                detailOverlay = SongListDetailState(t, sub, s, pid)
                            }
                        )
                        MainDestination.PLAYLISTS -> PlaylistsScreen(
                            vm = vm,
                            accent = accent,
                            onOpenSettings = { showSettings = true },
                            onOpenPlaylistDetail = { t, sub, s, pid ->
                                detailOverlay = SongListDetailState(t, sub, s, pid)
                            }
                        )
                        MainDestination.FOLDERS -> FoldersScreen(
                            vm = vm,
                            accent = accent,
                            onOpenSettings = { showSettings = true },
                            onOpenFolderSongs = { t, sub, s ->
                                detailOverlay = SongListDetailState(t, sub, s, null)
                            }
                        )
                    }

                    detailOverlay?.let { detail ->
                        DetailSongListOverlay(
                            detail = detail,
                            vm = vm,
                            accent = accent,
                            reducedMotion = settings.reducedMotion,
                            onBack = { detailOverlay = null },
                            onOpenSettings = { showSettings = true },
                            onOpenPlaylistPickerForSongs = { songsForPlaylistPicker = it },
                            onOpenMetadataEditor = { songForMetadataEditor = it }
                        )
                    }
                }
            }
        }

        // Full-Screen Audio Player Overlay
        AnimatedVisibility(
            visible = showNowPlaying && currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            currentSong?.let { activeSong ->
                NowPlayingScreen(
                    song = activeSong,
                    vm = vm,
                    accent = accent,
                    onCollapse = { showNowPlaying = false },
                    onOpenEqualizer = { showEqualizer = true },
                    onOpenSettings = { showSettings = true },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onOpenSpeedDialog = { showSpeedDialog = true },
                    onOpenPlaylistPicker = { songsForPlaylistPicker = listOf(activeSong.id) },
                    onOpenMetadataEditor = { songForMetadataEditor = activeSong },
                    onOpenOutputDeviceDialog = {
                        vm.refreshOutputDevices()
                        showOutputDeviceDialog = true
                    }
                )
            }
        }

        // Settings Overlay (opens on top of any screen or Now Playing and returns cleanly on Back)
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                SettingsScreen(
                    vm = vm,
                    accent = accent,
                    onBack = { showSettings = false },
                    onOpenEqualizer = { showEqualizer = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onOpenSpeedDialog = { showSpeedDialog = true }
                )
            }
        }

        // Equalizer Overlay (opens from Audio Player header or Settings and returns cleanly on Back)
        AnimatedVisibility(
            visible = showEqualizer,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                EqualizerScreen(
                    vm = vm,
                    accent = accent,
                    onBack = { showEqualizer = false }
                )
            }
        }
    }

    if (showQueueSheet) {
        QueueModalSheet(
            vm = vm,
            accent = accent,
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerPickerDialog(
            accent = accent,
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showSpeedDialog) {
        SpeedPickerDialog(
            currentSpeed = playbackSpeed,
            accent = accent,
            onSelect = { vm.setPlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showOutputDeviceDialog) {
        OutputDevicePickerDialog(
            vm = vm,
            accent = accent,
            onDismiss = { showOutputDeviceDialog = false }
        )
    }

    songsForPlaylistPicker?.let { songIds ->
        PlaylistSelectorDialog(
            songIds = songIds,
            playlists = playlists,
            accent = accent,
            onAddToPlaylist = { playlist ->
                vm.addSongsToPlaylist(playlist, songIds)
                songsForPlaylistPicker = null
            },
            onCreateNewPlaylist = { name ->
                vm.createPlaylist(name, songIds)
                songsForPlaylistPicker = null
            },
            onDismiss = { songsForPlaylistPicker = null }
        )
    }

    songForMetadataEditor?.let { targetSong ->
        MetadataEditorDialog(
            song = targetSong,
            vm = vm,
            accent = accent,
            onDismiss = { songForMetadataEditor = null }
        )
    }
}

@Composable
private fun OutputDevicePickerDialog(
    vm: AuraViewModel,
    accent: Color,
    onDismiss: () -> Unit
) {
    val devices by vm.outputDevices.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Audio Output Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (devices.isEmpty()) {
                    Text("Phone Speaker (Default)", fontSize = 14.sp)
                } else {
                    devices.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (dev.isActive) accent.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    vm.selectOutputDevice(dev)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (dev.isBluetooth) Icons.Rounded.BluetoothAudio else Icons.Rounded.Speaker,
                                    contentDescription = null,
                                    tint = if (dev.isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = dev.name,
                                        fontWeight = if (dev.isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (dev.isActive) accent else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = dev.typeLabel,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (dev.isActive) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = accent)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        vm.openSystemAudioOutputPanel()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("System Bluetooth / Media Output")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun PermissionOnboardingView(
    accent: Color,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Welcome to Aura Audio Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "100% Offline Music Player. Grant local audio permission so Aura Audio Pro can scan MP3, FLAC, WAV, AAC, M4A, and OGG tracks stored on your device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Audio Permission", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) {
            Text("Open System App Settings", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailSongListOverlay(
    detail: SongListDetailState,
    vm: AuraViewModel,
    accent: Color,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylistPickerForSongs: (List<Long>) -> Unit,
    onOpenMetadataEditor: (Song) -> Unit
) {
    val currentSong by vm.currentSong.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val allSongs by vm.allSongs.collectAsState()

    val currentPlaylist = remember(playlists, detail.playlistId) {
        playlists.firstOrNull { it.id == detail.playlistId }
    }
    val displayedSongs = remember(currentPlaylist, allSongs, detail.songs) {
        if (currentPlaylist != null) {
            val map = allSongs.associateBy { it.id }
            currentPlaylist.songIds.mapNotNull { map[it] }
        } else {
            detail.songs
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(detail.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${displayedSongs.size} tracks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (displayedSongs.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { vm.playSongs(displayedSongs, 0) },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play All", fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(displayedSongs, key = { idx, s -> "${s.id}_$idx" }) { idx, song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentPlaylist != null && displayedSongs.size > 1) {
                            Column {
                                IconButton(
                                    onClick = {
                                        if (idx > 0) vm.moveSongInPlaylist(currentPlaylist, idx, idx - 1)
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (idx < displayedSongs.lastIndex) vm.moveSongInPlaylist(currentPlaylist, idx, idx + 1)
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            SongListItem(
                                song = song,
                                isCurrent = currentSong?.id == song.id,
                                isPlaying = isPlaying,
                                isFavorite = favoriteIds.contains(song.id),
                                isSelected = false,
                                selectionMode = false,
                                reducedMotion = reducedMotion,
                                accent = accent,
                                onClick = { vm.playSongs(displayedSongs, idx) },
                                onToggleSelect = {},
                                onToggleFavorite = { vm.toggleFavorite(song.id) },
                                onPlayNext = { vm.playNextInQueue(song) },
                                onAddToPlaylist = { onOpenPlaylistPickerForSongs(listOf(song.id)) },
                                onEditMetadata = { onOpenMetadataEditor(song) },
                                onShare = { vm.shareSong(song) },
                                onRemoveFromPlaylist = detail.playlistId?.let { pid ->
                                    { vm.removeSongFromPlaylist(pid, song.id) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueModalSheet(
    vm: AuraViewModel,
    accent: Color,
    onDismiss: () -> Unit
) {
    val queue by vm.queue.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val isShuffle by vm.isShuffle.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playback Queue (${queue.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (queue.isNotEmpty()) {
                    TextButton(onClick = {
                        vm.saveQueueAsPlaylist("Saved Queue")
                        onDismiss()
                    }) {
                        Text("Save as Playlist", color = accent)
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                itemsIndexed(queue) { idx, song ->
                    val active = currentSong?.id == song.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) accent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { vm.playSongs(queue, idx, isShuffle) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) accent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(song.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (queue.size > 1) {
                            IconButton(onClick = { vm.removeFromQueue(idx) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerPickerDialog(
    accent: Color,
    onDismiss: () -> Unit
) {
    val options = listOf(
        0 to "Disable Sleep Timer",
        15 to "15 Minutes",
        30 to "30 Minutes",
        45 to "45 Minutes",
        60 to "60 Minutes",
        90 to "90 Minutes"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                options.forEach { (mins, label) ->
                    TextButton(
                        onClick = {
                            EqualizerController.startSleepTimer(mins)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, color = if (mins == 0) Color(0xFFF43F5E) else accent)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SpeedPickerDialog(
    currentSpeed: Float,
    accent: Color,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column {
                speeds.forEach { sp ->
                    TextButton(
                        onClick = {
                            onSelect(sp)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${sp}x" + if (sp == 1.0f) " (Normal)" else "",
                            fontWeight = if (currentSpeed == sp) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (currentSpeed == sp) accent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun PlaylistSelectorDialog(
    songIds: List<Long>,
    playlists: List<PlaylistModel>,
    accent: Color,
    onAddToPlaylist: (PlaylistModel) -> Unit,
    onCreateNewPlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${songIds.size} Track(s) to Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                playlists.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onAddToPlaylist(p) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text("${p.songIds.size} songs", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Or create new playlist...") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) onCreateNewPlaylist(newName)
                },
                enabled = newName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) {
                Text("Create & Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MetadataEditorDialog(
    song: Song,
    vm: AuraViewModel,
    accent: Color,
    onDismiss: () -> Unit
) {
    val overrides by vm.metadataOverrides.collectAsState()
    val existing = overrides[song.id]

    var title by remember(song) { mutableStateOf(existing?.title ?: song.title) }
    var artist by remember(song) { mutableStateOf(existing?.artist ?: song.artist) }
    var album by remember(song) { mutableStateOf(existing?.album ?: song.album) }
    var genre by remember(song) { mutableStateOf(existing?.genre ?: song.genre) }
    var yearStr by remember(song) { mutableStateOf((existing?.year ?: song.year).takeIf { it > 0 }?.toString().orEmpty()) }
    var customLyrics by remember(song) { mutableStateOf(existing?.customLyrics.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Metadata & Offline LRC Lyrics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = { yearStr = it },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                OutlinedTextField(
                    value = customLyrics,
                    onValueChange = { customLyrics = it },
                    label = { Text("Synchronized LRC Lyrics ([00:12.00]Line...)") },
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.saveSongMetadataOverride(
                        song = song,
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        year = yearStr.toIntOrNull() ?: 0,
                        customLyrics = customLyrics
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
