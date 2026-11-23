package com.aura.audiopro.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.aura.audiopro.audio.EqualizerController
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.model.Song
import com.aura.audiopro.ui.components.AlbumArtView
import com.aura.audiopro.ui.components.RotatingVinylArtwork
import com.aura.audiopro.ui.components.WaveformSeekBar
import com.aura.audiopro.ui.components.bounceClick
import com.aura.audiopro.ui.viewmodel.AuraViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full-Screen Audio Player with ProMusicPlayer visual CSS, dynamic dominant artwork gradient,
 * interactive vertical drag-to-dismiss, directional artwork transition, and spring physics.
 * @author Gopal Gorchhiya
 */
@Composable
fun NowPlayingScreen(
    song: Song,
    vm: AuraViewModel,
    accent: Color,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    onOpenMetadataEditor: () -> Unit,
    onOpenOutputDeviceDialog: () -> Unit
) {
    val isPlaying by vm.isPlaying.collectAsState()
    val currentPos by vm.currentPosition.collectAsState()
    val totalDur by vm.totalDuration.collectAsState()
    val isShuffle by vm.isShuffle.collectAsState()
    val repeatMode by vm.repeatMode.collectAsState()
    val playbackSpeed by vm.playbackSpeed.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val dominantColor by vm.dominantColor.collectAsState()
    val lyrics by vm.currentLyrics.collectAsState()
    val techDetails by vm.currentTechDetails.collectAsState()
    val settings by vm.settings.collectAsState()
    val outputDevices by vm.outputDevices.collectAsState()
    val sleepSec by EqualizerController.sleepTimerRemainingSec.collectAsState()

    val isFavorite = favoriteIds.contains(song.id)
    var showLyricsPanel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshOutputDevices()
    }

    val activeOutputDevice = remember(outputDevices) {
        outputDevices.firstOrNull { it.isActive }
    }

    val domTint by animateColorAsState(
        targetValue = if (settings.dynamicArtworkColor) dominantColor else Color(0xFF2A2A3A),
        animationSpec = tween(durationMillis = 600),
        label = "dominant"
    )
    val bgBase = Color(0xFF0A0A0F)

    val heartAnim = remember { Animatable(1f) }
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            heartAnim.animateTo(1.32f, spring(dampingRatio = 0.45f, stiffness = 900f))
            heartAnim.animateTo(1.0f, spring(dampingRatio = 0.6f, stiffness = 500f))
        }
    }

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val safeTotal = totalDur.coerceAtLeast(1L)
    val progress = (currentPos.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        domTint.copy(alpha = 0.78f),
                        bgBase,
                        bgBase
                    )
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val nextY = (offsetY.value + dragAmount).coerceAtLeast(0f)
                            offsetY.snapTo(nextY)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value > 280f) {
                                onCollapse()
                                offsetY.snapTo(0f)
                            } else {
                                offsetY.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.65f, stiffness = 500f)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetY.animateTo(0f, spring())
                        }
                    }
                )
            }
            .systemBarsPadding()
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkOrVinylBox(
                        song = song,
                        isPlaying = isPlaying,
                        vinylMode = settings.vinylMode,
                        reducedMotion = settings.reducedMotion,
                        accent = accent,
                        onSwipePrev = { vm.skipPrevious() },
                        onSwipeNext = { vm.skipNext() }
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    NowPlayingTopBar(
                        album = song.album,
                        showLyrics = showLyricsPanel,
                        sleepActive = sleepSec > 0,
                        playbackSpeed = playbackSpeed,
                        accent = accent,
                        onCollapse = onCollapse,
                        onOpenEqualizer = onOpenEqualizer,
                        onOpenSleepTimer = onOpenSleepTimer,
                        onOpenSettings = onOpenSettings,
                        onToggleLyrics = { showLyricsPanel = !showLyricsPanel },
                        onOpenSpeedDialog = onOpenSpeedDialog,
                        onOpenPlaylistPicker = onOpenPlaylistPicker,
                        onEditMetadata = onOpenMetadataEditor,
                        onShare = { vm.shareSong(song) }
                    )
                    NowPlayingTitleRow(
                        song = song,
                        techSummary = techDetails?.let {
                            "${it.formatLabel} • ${it.bitrateKbps} kbps • ${it.sampleRateHz} Hz • ${LyricsAndMetadataHelper.formatFileSize(song.size)}"
                        } ?: LyricsAndMetadataHelper.formatFileSize(song.size),
                        isFavorite = isFavorite,
                        heartScale = heartAnim.value,
                        accent = accent,
                        onToggleFavorite = { vm.toggleFavorite(song.id) }
                    )
                    WaveformSeekBar(
                        songId = song.id,
                        progress = progress,
                        accent = accent,
                        onSeek = { frac -> vm.seekTo((frac * safeTotal).toLong()) }
                    )
                    NowPlayingTransportControls(
                        isPlaying = isPlaying,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        accent = accent,
                        onToggleShuffle = { vm.toggleShuffle() },
                        onPrev = { vm.skipPrevious() },
                        onPlayPause = { vm.togglePlayPause() },
                        onNext = { vm.skipNext() },
                        onCycleRepeat = { vm.cycleRepeatMode() }
                    )
                    NowPlayingCleanFooter(
                        outputDeviceName = activeOutputDevice?.name ?: "Phone Speaker",
                        isBluetooth = activeOutputDevice?.isBluetooth == true,
                        accent = accent,
                        onOpenOutputDevices = onOpenOutputDeviceDialog,
                        onOpenQueue = onOpenQueue
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Subtle pill drag handle at top of FullPlayer
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 4.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                )

                NowPlayingTopBar(
                    album = song.album,
                    showLyrics = showLyricsPanel,
                    sleepActive = sleepSec > 0,
                    playbackSpeed = playbackSpeed,
                    accent = accent,
                    onCollapse = onCollapse,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenSleepTimer = onOpenSleepTimer,
                    onOpenSettings = onOpenSettings,
                    onToggleLyrics = { showLyricsPanel = !showLyricsPanel },
                    onOpenSpeedDialog = onOpenSpeedDialog,
                    onOpenPlaylistPicker = onOpenPlaylistPicker,
                    onEditMetadata = onOpenMetadataEditor,
                    onShare = { vm.shareSong(song) }
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val availH = (maxHeight - 8.dp).coerceAtLeast(120.dp)
                    val side = (if (maxWidth < availH) maxWidth else availH).coerceAtLeast(120.dp)
                    Box(
                        modifier = Modifier.size(side),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showLyricsPanel) {
                            LyricsPanelView(
                                lyrics = lyrics,
                                currentPosMs = currentPos,
                                accent = accent,
                                onSeekToLine = { vm.seekTo(it) },
                                onEditLyrics = onOpenMetadataEditor
                            )
                        } else {
                            ArtworkOrVinylBox(
                                song = song,
                                isPlaying = isPlaying,
                                vinylMode = settings.vinylMode,
                                reducedMotion = settings.reducedMotion,
                                accent = accent,
                                onSwipePrev = { vm.skipPrevious() },
                                onSwipeNext = { vm.skipNext() }
                            )
                        }
                    }
                }

                NowPlayingTitleRow(
                    song = song,
                    techSummary = techDetails?.let {
                        "${it.formatLabel} • ${it.bitrateKbps} kbps • ${it.sampleRateHz} Hz • ${LyricsAndMetadataHelper.formatFileSize(song.size)}"
                    } ?: LyricsAndMetadataHelper.formatFileSize(song.size),
                    isFavorite = isFavorite,
                    heartScale = heartAnim.value,
                    accent = accent,
                    onToggleFavorite = { vm.toggleFavorite(song.id) }
                )

                Spacer(Modifier.height(10.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    WaveformSeekBar(
                        songId = song.id,
                        progress = progress,
                        accent = accent,
                        onSeek = { frac -> vm.seekTo((frac * safeTotal).toLong()) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            LyricsAndMetadataHelper.formatDuration(currentPos),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA9A9BA)
                        )
                        Text(
                            LyricsAndMetadataHelper.formatDuration(totalDur),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA9A9BA)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                NowPlayingTransportControls(
                    isPlaying = isPlaying,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    accent = accent,
                    onToggleShuffle = { vm.toggleShuffle() },
                    onPrev = { vm.skipPrevious() },
                    onPlayPause = { vm.togglePlayPause() },
                    onNext = { vm.skipNext() },
                    onCycleRepeat = { vm.cycleRepeatMode() }
                )

                Spacer(Modifier.height(10.dp))

                NowPlayingCleanFooter(
                    outputDeviceName = activeOutputDevice?.name ?: "Phone Speaker",
                    isBluetooth = activeOutputDevice?.isBluetooth == true,
                    accent = accent,
                    onOpenOutputDevices = onOpenOutputDeviceDialog,
                    onOpenQueue = onOpenQueue
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    album: String,
    showLyrics: Boolean,
    sleepActive: Boolean,
    playbackSpeed: Float,
    accent: Color,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    onEditMetadata: () -> Unit,
    onShare: () -> Unit
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .bounceClick(scaleDown = 0.88f, onClick = onCollapse)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = if (showLyrics) "SYNCHRONIZED LYRICS" else "PLAYING FROM ALBUM",
                fontSize = 10.sp,
                color = Color(0xFFA9A9BA),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
            Text(
                text = album,
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onOpenEqualizer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Equalizer",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            IconButton(
                onClick = onOpenSleepTimer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = "Sleep Timer",
                    tint = if (sleepActive) accent else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box {
                IconButton(
                    onClick = { moreMenuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (showLyrics) "Hide Lyrics" else "Show Lyrics") },
                        leadingIcon = { Icon(Icons.Rounded.Lyrics, contentDescription = null) },
                        onClick = {
                            moreMenuExpanded = false
                            onToggleLyrics()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Playback Speed (${playbackSpeed}x)") },
                        leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
                        onClick = {
                            moreMenuExpanded = false
                            onOpenSpeedDialog()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            moreMenuExpanded = false
                            onOpenPlaylistPicker()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Tags & Lyrics") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            moreMenuExpanded = false
                            onEditMetadata()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Audio") },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            moreMenuExpanded = false
                            onShare()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCleanFooter(
    outputDeviceName: String,
    isBluetooth: Boolean,
    accent: Color,
    onOpenOutputDevices: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .bounceClick(scaleDown = 0.94f, onClick = onOpenOutputDevices)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF22222E))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isBluetooth) Icons.Rounded.BluetoothAudio else Icons.Rounded.Speaker,
                contentDescription = "Audio Output Device",
                tint = accent,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = outputDeviceName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier
                .bounceClick(scaleDown = 0.94f, onClick = onOpenQueue)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF22222E))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = "Playing Queue",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Queue",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ArtworkOrVinylBox(
    song: Song,
    isPlaying: Boolean,
    vinylMode: Boolean,
    reducedMotion: Boolean,
    accent: Color,
    onSwipePrev: () -> Unit,
    onSwipeNext: () -> Unit
) {
    var cumulativeDrag by remember { mutableFloatStateOf(0f) }
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying || reducedMotion) 1.0f else 0.84f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 220f
        ),
        label = "art_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = artScale
                scaleY = artScale
            }
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onDragStart = { cumulativeDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> cumulativeDrag += dragAmount },
                    onDragEnd = {
                        if (cumulativeDrag > 85f) onSwipePrev()
                        else if (cumulativeDrag < -85f) onSwipeNext()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (vinylMode) {
            RotatingVinylArtwork(
                song = song,
                isPlaying = isPlaying,
                reducedMotion = reducedMotion,
                accent = accent,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AnimatedContent(
                targetState = song,
                transitionSpec = {
                    (slideInHorizontally(tween(350)) { width -> width / 2 } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(350)) { width -> -width / 2 } + fadeOut(tween(200)))
                },
                label = "artwork_slide"
            ) { targetSong ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(26.dp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black
                        )
                        .clip(RoundedCornerShape(26.dp))
                ) {
                    AlbumArtView(
                        song = targetSong,
                        sizePx = 512,
                        accent = accent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingTitleRow(
    song: Song,
    techSummary: String,
    isFavorite: Boolean,
    heartScale: Float,
    accent: Color,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 22.sp,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = song.artist,
                fontSize = 15.sp,
                color = Color(0xFFA9A9BA),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = techSummary,
                    fontSize = 10.sp,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(heartScale)
                .bounceClick(scaleDown = 0.85f, onClick = onToggleFavorite)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF5C9D) else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun NowPlayingTransportControls(
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeatMode: Int,
    accent: Color,
    onToggleShuffle: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .bounceClick(scaleDown = 0.88f, onClick = onToggleShuffle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) accent else Color(0xFFA9A9BA),
                    modifier = Modifier.size(22.dp)
                )
            }
            if (isShuffle) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .bounceClick(scaleDown = 0.88f, onClick = onPrev)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .bounceClick(scaleDown = 0.90f, onClick = onPlayPause)
                .shadow(16.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.Black,
                modifier = Modifier.size(38.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .bounceClick(scaleDown = 0.88f, onClick = onNext)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .bounceClick(scaleDown = 0.88f, onClick = onCycleRepeat),
                contentAlignment = Alignment.Center
            ) {
                val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                val tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color(0xFFA9A9BA)
                Icon(
                    imageVector = icon,
                    contentDescription = "Repeat",
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (repeatMode != Player.REPEAT_MODE_OFF) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun LyricsPanelView(
    lyrics: List<com.aura.audiopro.model.LrcLine>,
    currentPosMs: Long,
    accent: Color,
    onSeekToLine: (Long) -> Unit,
    onEditLyrics: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
        color = Color(0xFF14141C).copy(alpha = 0.88f)
    ) {
        if (lyrics.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = accent, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    "No offline .lrc file or embedded lyrics found for this song.",
                    color = Color(0xFFA9A9BA),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onEditLyrics) {
                    Text("Add / Paste LRC Lyrics", color = accent)
                }
            }
        } else {
            val activeIndex = lyrics.indexOfLast { it.timeMs <= currentPosMs }.coerceAtLeast(0)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(lyrics) { idx, line ->
                    val isActive = idx == activeIndex
                    Text(
                        text = line.text,
                        fontSize = if (isActive) 18.sp else 14.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isActive) accent else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekToLine(line.timeMs) }
                    )
                }
            }
        }
    }
}
