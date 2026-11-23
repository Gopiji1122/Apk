package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.audiopro.model.PlaylistModel
import com.aura.audiopro.model.Song
import com.aura.audiopro.ui.components.ProIconBox
import com.aura.audiopro.ui.components.ProPillButton
import com.aura.audiopro.ui.components.bounceClick
import com.aura.audiopro.ui.viewmodel.AuraViewModel

@Composable
fun PlaylistsScreen(
    vm: AuraViewModel,
    accent: Color,
    onOpenSettings: () -> Unit,
    onOpenPlaylistDetail: (String, String, List<Song>, Long?) -> Unit
) {
    val allSongs by vm.allSongs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val recentIds by vm.recentlyPlayedIds.collectAsState()
    val playCounts by vm.playHistoryMap.collectAsState()
    val queue by vm.queue.collectAsState()

    val songMap = remember(allSongs) { allSongs.associateBy { it.id } }
    val favSongs = remember(allSongs, favoriteIds) { allSongs.filter { favoriteIds.contains(it.id) } }
    val recentSongs = remember(recentIds, songMap) { recentIds.mapNotNull { songMap[it] } }
    val mostPlayedSongs = remember(allSongs, playCounts) {
        allSongs.filter { (playCounts[it.id] ?: 0) > 0 }.sortedByDescending { playCounts[it.id] ?: 0 }
    }
    val addedSongs = remember(allSongs) { allSongs.sortedByDescending { it.dateAdded }.take(50) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistModel?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playlist",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (queue.isNotEmpty()) {
                    ProPillButton(
                        text = "Save Queue",
                        icon = Icons.Rounded.Save,
                        primary = false,
                        onClick = { showSaveQueueDialog = true }
                    )
                }
                ProPillButton(
                    text = "New",
                    icon = Icons.Rounded.Add,
                    primary = true,
                    onClick = { showCreateDialog = true }
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bounceClick(scaleDown = 0.90f, onClick = onOpenSettings)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "AUTO PLAYLISTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            item {
                SmartPlaylistRow(
                    title = "Liked songs",
                    subtitle = "${favSongs.size} songs",
                    icon = Icons.Rounded.Favorite,
                    tint = Color(0xFFFF5C9D),
                    onClick = { onOpenPlaylistDetail("Liked songs", "${favSongs.size} liked tracks", favSongs, null) }
                )
            }

            item {
                SmartPlaylistRow(
                    title = "Recently played",
                    subtitle = "${recentSongs.size} songs",
                    icon = Icons.Rounded.History,
                    tint = Color(0xFF3FA2FF),
                    onClick = { onOpenPlaylistDetail("Recently played", "${recentSongs.size} tracks", recentSongs, null) }
                )
            }

            item {
                SmartPlaylistRow(
                    title = "Most played",
                    subtitle = "${mostPlayedSongs.size} songs",
                    icon = Icons.Rounded.TrendingUp,
                    tint = Color(0xFFFFCF3F),
                    onClick = { onOpenPlaylistDetail("Most played", "${mostPlayedSongs.size} tracks", mostPlayedSongs, null) }
                )
            }

            item {
                SmartPlaylistRow(
                    title = "Recently added",
                    subtitle = "${addedSongs.size} songs",
                    icon = Icons.Rounded.NewReleases,
                    tint = accent,
                    onClick = { onOpenPlaylistDetail("Recently added", "${addedSongs.size} tracks", addedSongs, null) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "YOUR PLAYLISTS (${playlists.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        letterSpacing = 1.1.sp
                    )
                    if (playlists.isNotEmpty()) {
                        TextButton(onClick = { showExportDialog = true }) {
                            Text("Export JSON", fontSize = 12.sp, color = accent)
                        }
                    }
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tap '+ New' above to create a custom playlist.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    val resolvedSongs = playlist.songIds.mapNotNull { songMap[it] }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(scaleDown = 0.97f) {
                                onOpenPlaylistDetail(
                                    playlist.name,
                                    "${resolvedSongs.size} tracks",
                                    resolvedSongs,
                                    playlist.id
                                )
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProIconBox(icon = Icons.Rounded.QueueMusic, tint = accent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${resolvedSongs.size} songs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { playlistToRename = playlist }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.deletePlaylist(playlist.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.createPlaylist(name)
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSaveQueueDialog) {
        var name by remember { mutableStateOf("Queue Mix") }
        AlertDialog(
            onDismissRequest = { showSaveQueueDialog = false },
            title = { Text("Save Current Queue as Playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.saveQueueAsPlaylist(name)
                        showSaveQueueDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveQueueDialog = false }) { Text("Cancel") }
            }
        )
    }

    playlistToRename?.let { target ->
        var newName by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.renamePlaylist(target.id, newName)
                        playlistToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (showExportDialog) {
        val exportedJson = remember(playlists) { vm.exportPlaylistsJson() }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exported Playlists Backup (JSON)") },
            text = {
                OutlinedTextField(
                    value = exportedJson,
                    onValueChange = {},
                    readOnly = true,
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SmartPlaylistRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProIconBox(icon = icon, tint = tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
