package com.aura.audiopro.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.model.Song
import com.aura.audiopro.ui.components.AlbumArtView
import com.aura.audiopro.ui.components.RotatingVinylArtwork
import com.aura.audiopro.ui.components.WaveformSeekBar
import com.aura.audiopro.ui.viewmodel.AuraViewModel

@Composable
fun NowPlayingScreen(
    song: Song,
    vm: AuraViewModel,
    accent: Color,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenPlaylistPicker: () -> Unit,
    onOpenMetadataEditor: () -> Unit
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

    val isFavorite = favoriteIds.contains(song.id)
    var showLyricsPanel by remember { mutableStateOf(false) }

    val topTint by animateColorAsState(
        targetValue = if (settings.dynamicArtworkColor) dominantColor.copy(alpha = 0.55f) else accent.copy(alpha = 0.35f),
        animationSpec = tween(550),
        label = "bg_tint"
    )

    val heartScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fav_scale"
    )

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val safeTotal = totalDur.coerceAtLeast(1L)
    val progress = (currentPos.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topTint, Color(0xFF090D14), Color(0xFF05070B))
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 44f) onCollapse()
                }
            }
            .systemBarsPadding()
    ) {
        if (isLandscape) {
            // Landscape Adaptive Layout
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
                        onCollapse = onCollapse,
                        onToggleLyrics = { showLyricsPanel = !showLyricsPanel },
                        onEditMetadata = onOpenMetadataEditor,
                        onShare = { vm.shareSong(song) }
                    )
                    NowPlayingTitleRow(
                        song = song,
                        techSummary = techDetails?.let {
                            "${it.formatLabel} • ${it.bitrateKbps} kbps • ${it.sampleRateHz} Hz • ${LyricsAndMetadataHelper.formatFileSize(song.size)}"
                        } ?: LyricsAndMetadataHelper.formatFileSize(song.size),
                        isFavorite = isFavorite,
                        heartScale = heartScale,
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
                }
            }
        } else {
            // Portrait Full-Screen Player Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                NowPlayingTopBar(
                    album = song.album,
                    showLyrics = showLyricsPanel,
                    onCollapse = onCollapse,
                    onToggleLyrics = { showLyricsPanel = !showLyricsPanel },
                    onEditMetadata = onOpenMetadataEditor,
                    onShare = { vm.shareSong(song) }
                )

                // Center Area: either Artwork/Vinyl or Synchronized Lyrics Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 10.dp),
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

                NowPlayingTitleRow(
                    song = song,
                    techSummary = techDetails?.let {
                        "${it.formatLabel} • ${it.bitrateKbps} kbps • ${it.sampleRateHz} Hz • ${LyricsAndMetadataHelper.formatFileSize(song.size)}"
                    } ?: LyricsAndMetadataHelper.formatFileSize(song.size),
                    isFavorite = isFavorite,
                    heartScale = heartScale,
                    accent = accent,
                    onToggleFavorite = { vm.toggleFavorite(song.id) }
                )

                Spacer(Modifier.height(10.dp))

                // Waveform + Time Labels
                Column(modifier = Modifier.fillMaxWidth()) {
                    WaveformSeekBar(
                        songId = song.id,
                        progress = progress,
                        accent = accent,
                        onSeek = { frac -> vm.seekTo((frac * safeTotal).toLong()) }
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            LyricsAndMetadataHelper.formatDuration(currentPos),
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            LyricsAndMetadataHelper.formatDuration(totalDur),
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
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

                Spacer(Modifier.height(8.dp))

                // Bottom Utility Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenSpeedDialog) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${playbackSpeed}x", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    }
                    IconButton(onClick = { showLyricsPanel = !showLyricsPanel }) {
                        Icon(
                            Icons.Rounded.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (showLyricsPanel) accent else Color(0xFFCBD5E1)
                        )
                    }
                    IconButton(onClick = onOpenPlaylistPicker) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to Playlist", tint = Color(0xFFCBD5E1))
                    }
                    IconButton(onClick = onOpenSleepTimer) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = "Sleep Timer", tint = Color(0xFFCBD5E1))
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = Color(0xFFCBD5E1))
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    album: String,
    showLyrics: Boolean,
    onCollapse: () -> Unit,
    onToggleLyrics: () -> Unit,
    onEditMetadata: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                if (showLyrics) "SYNCHRONIZED LYRICS" else "NOW PLAYING",
                fontSize = 10.sp,
                color = Color(0xFFCBD5E1),
                letterSpacing = 1.4.sp
            )
            Text(
                album,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row {
            IconButton(onClick = onEditMetadata) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit Tags", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
            }
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
    val scale by animateFloatAsState(
        targetValue = if (isPlaying || reducedMotion) 1.0f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "art_zoom"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxSize()
            .scale(scale)
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onDragStart = { cumulativeDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> cumulativeDrag += dragAmount },
                    onDragEnd = {
                        if (cumulativeDrag > 90f) onSwipePrev()
                        else if (cumulativeDrag < -90f) onSwipeNext()
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
            AlbumArtView(
                song = song,
                sizePx = 512,
                accent = accent,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
            )
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
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = techSummary,
                fontSize = 11.sp,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.scale(heartScale)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFF43F5E) else Color.White,
                modifier = Modifier.size(28.dp)
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
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShuffle) accent else Color(0xFF94A3B8)
            )
        }
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
        }
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accent,
                contentColor = Color.Black
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play/Pause",
                modifier = Modifier.size(40.dp)
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(38.dp))
        }
        IconButton(onClick = onCycleRepeat) {
            val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
            val tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color(0xFF94A3B8)
            Icon(icon, contentDescription = "Repeat", tint = tint)
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
            .clip(RoundedCornerShape(20.dp)),
        color = Color.Black.copy(alpha = 0.45f)
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
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
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
                        color = if (isActive) accent else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekToLine(line.timeMs) }
                    )
                }
            }
        }
    }
}
