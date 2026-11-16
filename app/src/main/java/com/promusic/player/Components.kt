package com.promusic.player

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Press-and-bounce click: the element springs slightly smaller while pressed. */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    this
        .scale(s)
        .clickable(interactionSource = source, indication = null) { onClick() }
}

@Composable
fun ArtImage(
    song: Song?,
    modifier: Modifier = Modifier,
    sizePx: Int = 256,
    shape: Shape = RoundedCornerShape(12.dp),
    iconSize: Dp = 24.dp
) {
    val ctx = LocalContext.current.applicationContext
    val bmp by produceState<Bitmap?>(ArtLoader.peek(song, sizePx), song?.id, sizePx) {
        value = ArtLoader.peek(song, sizePx)
        if (value == null && song != null) value = ArtLoader.load(ctx, song, sizePx)
    }
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF30304A), Color(0xFF1A1A28)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(iconSize)
        )
        Crossfade(targetState = bmp, animationSpec = tween(250), label = "art") { b ->
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun PlayingBars(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bars")
    val h1 by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "h1")
    val h2 by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "h2")
    val h3 by t.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "h3")
    Row(
        modifier.size(width = 16.dp, height = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(h1, h2, h3).forEach { h ->
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(if (active) h else 0.3f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun SongRow(
    vm: PlayerViewModel,
    song: Song,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val titleColor by animateColorAsState(if (isCurrent) primary else Color.White, label = "rowTitle")
    val liked = song.id in vm.favorites

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtImage(song, Modifier.size(52.dp), 128, RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist + "  |  " + song.album,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) {
            PlayingBars(vm.isPlaying, primary, Modifier.padding(horizontal = 8.dp))
        }
        Text(
            fmtTime(song.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                    onClick = { menu = false; vm.playNext(song) }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                    onClick = { menu = false; vm.addToQueue(song) }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                    onClick = { menu = false; vm.playlistTarget = song }
                )
                DropdownMenuItem(
                    text = { Text(if (liked) "Remove from liked" else "Add to liked") },
                    leadingIcon = { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                    onClick = { menu = false; vm.toggleFavorite(song) }
                )
                DropdownMenuItem(
                    text = { Text("Go to album") },
                    leadingIcon = { Icon(Icons.Rounded.Album, null) },
                    onClick = { menu = false; vm.openDetail(Detail(DetailType.ALBUM, song.albumId.toString())) }
                )
                DropdownMenuItem(
                    text = { Text("Go to artist") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    onClick = { menu = false; vm.openDetail(Detail(DetailType.ARTIST, song.artist)) }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                    onClick = {
                        menu = false
                        try {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, song.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(i, "Share song"))
                        } catch (_: Exception) {
                        }
                    }
                )
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text("Remove from playlist") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onRemove() }
                    )
                }
            }
        }
    }
}

@Composable
fun SongList(
    vm: PlayerViewModel,
    list: List<Song>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp)
    ) {
        if (header != null) {
            item { header() }
        }
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            SongRow(
                vm = vm,
                song = s,
                isCurrent = vm.current?.id == s.id,
                onRemove = if (onRemove != null) ({ onRemove(s) }) else null,
                onClick = { vm.play(list, i) }
            )
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
    )
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "pillBg"
    )
    val fg by animateColorAsState(if (selected) Color.Black else Color.White, label = "pillFg")
    Box(
        Modifier
            .bounceClick(onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun PillButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .bounceClick(onClick)
            .clip(RoundedCornerShape(50))
            .background(if (filled) primary else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (filled) Color.Black else Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IconBox(icon: ImageVector, modifier: Modifier = Modifier.size(52.dp)) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun InfoRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(72.dp))
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Playlist name") }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onCreate(name.trim())
                    onDismiss()
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PlaylistPickerDialog(vm: PlayerViewModel) {
    val song = vm.playlistTarget ?: return
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.playlistTarget = null },
        title = { Text("Add to playlist") },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Playlist name") }
                    )
                } else {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("New playlist")
                    }
                    val names = vm.playlists.keys.toList()
                    if (names.isEmpty()) {
                        Text(
                            "No playlists yet. Create your first one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        itemsIndexed(names) { _, n ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        vm.addToPlaylist(n, song)
                                        vm.playlistTarget = null
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(n, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        vm.createPlaylist(n)
                        vm.addToPlaylist(n, song)
                        vm.playlistTarget = null
                    }
                }) { Text("Create") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.playlistTarget = null }) { Text("Cancel") } }
    )
}
