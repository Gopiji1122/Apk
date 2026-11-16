package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.aura.audiopro.model.PlaylistModel
import com.aura.audiopro.model.Song
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

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistModel?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Playlist", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (queue.isNotEmpty()) {
                    OutlinedButton(onClick = { showSaveQueueDialog = true }) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Queue", fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
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
                    "Smart Offline Playlists",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                SmartPlaylistRow(
                    title = "Favorites",
                    subtitle = "${favSongs.size} liked tracks",
                    icon = Icons.Rounded.Favorite,
                    tint = Color(0xFFF43F5E),
                    onClick = { onOpenPlaylistDetail("Favorites", "${favSongs.size} liked tracks", favSongs, null) }
                )
            }

            item {
                SmartPlaylistRow(
                    title = "Recently Played",
                    subtitle = "${recentSongs.size} tracks played recently",
                    icon = Icons.Rounded.History,
                    tint = Color(0xFF06B6D4),
                    onClick = { onOpenPlaylistDetail("Recently Played", "${recentSongs.size} tracks", recentSongs, null) }
                )
            }

            item {
                SmartPlaylistRow(
                    title = "Most Played",
                    subtitle = "${mostPlayedSongs.size} top tracks",
                    icon = Icons.Rounded.TrendingUp,
                    tint = Color(0xFFF59E0B),
                    onClick = { onOpenPlaylistDetail("Most Played", "${mostPlayedSongs.size} tracks", mostPlayedSongs, null) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Custom Playlists (${playlists.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    if (playlists.isNotEmpty()) {
                        TextButton(onClick = { showExportDialog = true }) {
                            Text("Export JSON", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tap '+ New' above to create your first custom playlist.",
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                onOpenPlaylistDetail(
                                    playlist.name,
                                    "${resolvedSongs.size} tracks",
                                    resolvedSongs,
                                    playlist.id
                                )
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = accent)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, fontWeight = FontWeight.Bold)
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
                    Text("Create")
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
                    Text("Save")
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
                    Text("Save")
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}
