package com.aura.audiopro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.audiopro.core.AlbumArtCache
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.core.decodeAlbumBitmap
import com.aura.audiopro.model.Song
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * ProMusicPlayer tactile spring bounce click modifier (dampingRatio = 0.55f, stiffness = 1500f).
 * @author Gopal Gorchhiya
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.95f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 1500f
        ),
        label = "press"
    )
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
fun ProPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillBg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(220),
        label = "pillBg"
    )
    val pillFg by animateColorAsState(
        targetValue = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(220),
        label = "pillFg"
    )
    Box(
        modifier = modifier
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(pillBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = pillFg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun ProPillButton(
    text: String,
    icon: ImageVector,
    primary: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (primary) Color.Black else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun ProIconBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    boxSize: Dp = 44.dp,
    cornerRadius: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun ProSectionTitle(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (action != null && onAction != null) {
            Box(
                modifier = Modifier
                    .bounceClick(scaleDown = 0.94f, onClick = onAction)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = action,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AlbumArtView(
    song: Song?,
    modifier: Modifier = Modifier,
    sizePx: Int = 256,
    accent: Color = Color(0xFF1ED760)
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
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF30304A), Color(0xFF1A1A28))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = imgBitmap,
            animationSpec = tween(durationMillis = 220),
            label = "art"
        ) { bmp ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
                        tint = Color.White.copy(alpha = 0.28f),
                        modifier = Modifier.fillMaxSize(0.45f)
                    )
                }
            }
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
            .shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(Color(0xFF101018))
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
                .background(Color(0xFF0A0A0F))
                .border(2.dp, accent.copy(alpha = 0.5f), CircleShape)
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
            modifier = modifier.width(16.dp).height(14.dp),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.3f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "bars")
    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(460), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        modifier = modifier.width(16.dp).height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(h1, h2, h3).forEach { h ->
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
    val barCount = 46
    val heights = remember(songId) {
        List(barCount) { idx ->
            val v = sin((songId % 97L + idx * 1.7f).toDouble()).toFloat()
            (0.26f + abs(v) * 0.74f).coerceIn(0.24f, 1.0f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
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
        val barW = (slotW * 0.56f).coerceAtLeast(3f)

        heights.forEachIndexed { idx, normH ->
            val barFraction = idx.toFloat() / barCount.toFloat()
            val barH = totalH * normH
            val topY = (totalH - barH) / 2f
            val leftX = idx * slotW + (slotW - barW) / 2f
            val color = if (barFraction <= progress) accent else Color.White.copy(alpha = 0.20f)
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

    val rowTitleColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(220),
        label = "rowTitle"
    )
    val rowBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "rowBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .bounceClick(scaleDown = 0.97f) {
                if (selectionMode) onToggleSelect() else onClick()
            }
            .clip(RoundedCornerShape(14.dp))
            .background(rowBgColor)
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

        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            AlbumArtView(
                song = song,
                sizePx = 144,
                accent = accent,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.52f)),
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
                color = rowTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${song.artist}  |  ${song.album}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = LyricsAndMetadataHelper.formatDuration(song.duration),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        Box(
            modifier = Modifier
                .size(34.dp)
                .bounceClick(scaleDown = 0.85f, onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF5C9D) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }

        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
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
                        text = { Text("Delete from Device", color = Color(0xFFFF5C9D)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFFF5C9D)) },
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
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val progress = if (totalDuration > 0L) {
        (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val scope = rememberCoroutineScope()
    val offsetX = remember(song.id) { Animatable(0f) }

    val cardColor = if (MaterialTheme.colorScheme.background == Color(0xFFF6F6FA)) {
        MaterialTheme.colorScheme.surface
    } else {
        Color(0xFF1D1D2A)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .graphicsLayer {
                translationX = offsetX.value
                alpha = (1f - (abs(offsetX.value) / 520f)).coerceIn(0f, 1f)
            }
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (abs(offsetX.value) > 130f) {
                                val target = if (offsetX.value > 0f) 900f else -900f
                                offsetX.animateTo(target, tween(180))
                                offsetX.snapTo(0f)
                                onDismiss()
                            } else {
                                offsetX.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.65f, stiffness = 500f)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, spring())
                        }
                    }
                )
            }
            .bounceClick(scaleDown = 0.97f, onClick = onExpand)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = song,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "mini_song",
                    modifier = Modifier.weight(1f)
                ) { current ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AlbumArtView(
                            song = current,
                            sizePx = 128,
                            accent = accent,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = current.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = current.artist,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .bounceClick(scaleDown = 0.88f, onClick = onPlayPause)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }
    }
}
