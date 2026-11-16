package com.aura.audiopro.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.audiopro.core.AlbumArtCache
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.core.decodeAlbumBitmap
import com.aura.audiopro.model.Song
import kotlin.math.sin

@Composable
fun AlbumArtView(
    song: Song?,
    modifier: Modifier = Modifier,
    sizePx: Int = 256,
    accent: Color = Color(0xFF1DB954)
) {
    val context = LocalContext.current
    var imgBitmap by remember(song?.albumId, sizePx) {
        val cached = song?.let { AlbumArtCache.getBitmap("${it.albumId}_$sizePx") }
        mutableStateOf<ImageBitmap?>(cached?.asImageBitmap())
    }

    LaunchedEffect(song?.albumId, sizePx) {
        if (song == null) {
            imgBitmap = null
            return@LaunchedEffect
        }
        val cached = AlbumArtCache.getBitmap("${song.albumId}_$sizePx")
        if (cached != null) {
            imgBitmap = cached.asImageBitmap()
        } else {
            val loaded = decodeAlbumBitmap(context, song.uri, song.albumArtUri, song.albumId, sizePx)
            imgBitmap = loaded?.asImageBitmap()
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bmp = imgBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = song?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = accent.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxSize(0.46f)
            )
        }
    }
}

@Composable
fun RotatingVinylArtwork(
    song: Song?,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying && !reducedMotion) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "deg"
    )

    Box(
        modifier = modifier
            .rotate(if (isPlaying && !reducedMotion) angle else 0f)
            .clip(CircleShape)
            .background(Color(0xFF0E131A))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        AlbumArtView(
            song = song,
            sizePx = 512,
            accent = accent,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF0A0D12))
        )
    }
}

@Composable
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    reducedMotion: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (!isPlaying || reducedMotion) {
        Row(
            modifier = modifier.width(18.dp).height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.35f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "eq")
    val b1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = "b1"
    )
    val b2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(320), RepeatMode.Reverse),
        label = "b2"
    )
    val b3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        modifier = modifier.width(18.dp).height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(b1, b2, b3).forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun WaveformSeekBar(
    songId: Long,
    progress: Float,
    accent: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val barCount = 44
    val heights = remember(songId) {
        List(barCount) { idx ->
            val v = sin((songId % 97L + idx * 1.7f).toDouble()).toFloat()
            (0.28f + kotlin.math.abs(v) * 0.72f).coerceIn(0.25f, 1.0f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
    ) {
        val totalW = size.width
        val totalH = size.height
        val slotW = totalW / barCount
        val barW = (slotW * 0.58f).coerceAtLeast(3f)

        heights.forEachIndexed { idx, normH ->
            val barFraction = idx.toFloat() / barCount.toFloat()
            val barH = totalH * normH
            val topY = (totalH - barH) / 2f
            val leftX = idx * slotW + (slotW - barW) / 2f
            val color = if (barFraction <= progress) accent else Color.White.copy(alpha = 0.24f)
            drawRoundRect(
                color = color,
                topLeft = Offset(leftX, topY),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    reducedMotion: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditMetadata: () -> Unit,
    onShare: () -> Unit,
    onDeleteFile: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isSelected -> accent.copy(alpha = 0.22f)
                    isCurrent -> accent.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .clickable {
                if (selectionMode) onToggleSelect() else onClick()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = accent)
            )
            Spacer(Modifier.width(6.dp))
        }

        Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            AlbumArtView(
                song = song,
                sizePx = 144,
                accent = accent,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(
                        isPlaying = isPlaying,
                        reducedMotion = reducedMotion,
                        color = accent
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Text(
                text = "${song.artist} • ${song.album} • ${LyricsAndMetadataHelper.formatDuration(song.duration)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, contentDescription = null) },
                    onClick = { menuOpen = false; onPlayNext() }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                    onClick = { menuOpen = false; onAddToPlaylist() }
                )
                DropdownMenuItem(
                    text = { Text("Select Multiple") },
                    leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null) },
                    onClick = { menuOpen = false; onToggleSelect() }
                )
                DropdownMenuItem(
                    text = { Text("Edit Tags & Lyrics") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEditMetadata() }
                )
                DropdownMenuItem(
                    text = { Text("Share Audio") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = { menuOpen = false; onShare() }
                )
                if (onRemoveFromPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text("Remove from Playlist") },
                        leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null) },
                        onClick = { menuOpen = false; onRemoveFromPlaylist() }
                    )
                }
                if (onDeleteFile != null) {
                    DropdownMenuItem(
                        text = { Text("Delete from Device", color = Color(0xFFF43F5E)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFF43F5E)) },
                        onClick = { menuOpen = false; onDeleteFile() }
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    accent: Color,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val progress = if (totalDuration > 0L) {
        (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onExpand() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtView(
                    song = song,
                    sizePx = 128,
                    accent = accent,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                    Text(
                        text = song.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause"
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next"
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                color = accent,
                trackColor = accent.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }
    }
}
