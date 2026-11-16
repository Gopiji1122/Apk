package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.audiopro.model.*
import com.aura.audiopro.ui.components.AlbumArtView
import com.aura.audiopro.ui.components.SongListItem
import com.aura.audiopro.ui.viewmodel.AuraViewModel

@Composable
fun LibraryScreen(
    vm: AuraViewModel,
    accent: Color,
    onOpenPlaylistPickerForSongs: (List<Long>) -> Unit,
    onOpenMetadataEditor: (Song) -> Unit,
    onOpenSongListDetail: (String, String, List<Song>, Long?) -> Unit
) {
    val allSongs by vm.allSongs.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val selectedIds by vm.selectedSongIds.collectAsState()
    val sortOption by vm.sortOption.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val settings by vm.settings.collectAsState()
    val recentIds by vm.recentlyPlayedIds.collectAsState()
    val playCounts by vm.playHistoryMap.collectAsState()

    val subTabs = listOf(
        "All Songs",
        "Albums",
        "Artists",
        "Favorites",
        "Recently Played",
        "Recently Added",
        "Most Played"
    )
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }

    val filteredSongs = remember(allSongs, searchQuery) {
        if (searchQuery.isBlank()) allSongs
        else allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true) ||
                it.folderName.contains(searchQuery, ignoreCase = true)
        }
    }

    val songMap = remember(allSongs) { allSongs.associateBy { it.id } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header + Multi-Select Bulk Action Bar
        if (selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.clearSongSelection() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear Selection")
                    }
                    Text("${selectedIds.size} selected", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            onOpenPlaylistPickerForSongs(selectedIds.toList())
                            vm.clearSongSelection()
                        }
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add to Playlist")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Music Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Rounded.Sort, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(sortOption.label, color = accent, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = {
                                    vm.updateSortOption(opt)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Instant Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { vm.updateSearchQuery(it) },
            placeholder = { Text("Search songs, artists, albums...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { vm.updateSearchQuery("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.Transparent,
            contentColor = accent,
            edgePadding = 16.dp
        ) {
            subTabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (selectedSubTab == idx) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedSubTab == idx) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        when (selectedSubTab) {
            0 -> SongListView(
                songs = filteredSongs,
                vm = vm,
                currentSong = currentSong,
                isPlaying = isPlaying,
                favoriteIds = favoriteIds,
                selectedIds = selectedIds,
                reducedMotion = settings.reducedMotion,
                accent = accent,
                onOpenPlaylistPickerForSongs = onOpenPlaylistPickerForSongs,
                onOpenMetadataEditor = onOpenMetadataEditor,
                onConfirmDelete = { songToDelete = it }
            )
            1 -> {
                val albums = remember(filteredSongs) {
                    filteredSongs.groupBy { it.album }.entries.toList().sortedBy { it.key.lowercase() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(albums) { entry ->
                        val first = entry.value.first()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    onOpenSongListDetail(
                                        entry.key,
                                        "${first.artist} • ${entry.value.size} songs",
                                        entry.value,
                                        null
                                    )
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArtView(
                                song = first,
                                sizePx = 160,
                                accent = accent,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.key, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${first.artist} • ${entry.value.size} tracks",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
            2 -> {
                val artists = remember(filteredSongs) {
                    filteredSongs.groupBy { it.artist }.entries.toList().sortedBy { it.key.lowercase() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(artists) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    onOpenSongListDetail(
                                        entry.key,
                                        "${entry.value.size} tracks",
                                        entry.value,
                                        null
                                    )
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = accent)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.key, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${entry.value.size} songs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
            3 -> {
                val favList = remember(filteredSongs, favoriteIds) {
                    filteredSongs.filter { favoriteIds.contains(it.id) }
                }
                SongListView(
                    songs = favList,
                    vm = vm,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    selectedIds = selectedIds,
                    reducedMotion = settings.reducedMotion,
                    accent = accent,
                    onOpenPlaylistPickerForSongs = onOpenPlaylistPickerForSongs,
                    onOpenMetadataEditor = onOpenMetadataEditor,
                    onConfirmDelete = { songToDelete = it }
                )
            }
            4 -> {
                val recList = remember(recentIds, songMap) { recentIds.mapNotNull { songMap[it] } }
                SongListView(
                    songs = recList,
                    vm = vm,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    selectedIds = selectedIds,
                    reducedMotion = settings.reducedMotion,
                    accent = accent,
                    onOpenPlaylistPickerForSongs = onOpenPlaylistPickerForSongs,
                    onOpenMetadataEditor = onOpenMetadataEditor,
                    onConfirmDelete = { songToDelete = it }
                )
            }
            5 -> {
                val addedList = remember(filteredSongs) { filteredSongs.sortedByDescending { it.dateAdded } }
                SongListView(
                    songs = addedList,
                    vm = vm,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    selectedIds = selectedIds,
                    reducedMotion = settings.reducedMotion,
                    accent = accent,
                    onOpenPlaylistPickerForSongs = onOpenPlaylistPickerForSongs,
                    onOpenMetadataEditor = onOpenMetadataEditor,
                    onConfirmDelete = { songToDelete = it }
                )
            }
            6 -> {
                val topList = remember(filteredSongs, playCounts) {
                    filteredSongs.filter { (playCounts[it.id] ?: 0) > 0 }
                        .sortedByDescending { playCounts[it.id] ?: 0 }
                }
                SongListView(
                    songs = topList,
                    vm = vm,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    selectedIds = selectedIds,
                    reducedMotion = settings.reducedMotion,
                    accent = accent,
                    onOpenPlaylistPickerForSongs = onOpenPlaylistPickerForSongs,
                    onOpenMetadataEditor = onOpenMetadataEditor,
                    onConfirmDelete = { songToDelete = it }
                )
            }
        }
    }

    songToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("Delete Audio File?") },
            text = { Text("Are you sure you want to delete '${target.title}' from your device storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteSongFromDevice(target)
                        songToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E), contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SongListView(
    songs: List<Song>,
    vm: AuraViewModel,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    selectedIds: Set<Long>,
    reducedMotion: Boolean,
    accent: Color,
    onOpenPlaylistPickerForSongs: (List<Long>) -> Unit,
    onOpenMetadataEditor: (Song) -> Unit,
    onConfirmDelete: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No matching tracks in this view.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
            SongListItem(
                song = song,
                isCurrent = currentSong?.id == song.id,
                isPlaying = isPlaying,
                isFavorite = favoriteIds.contains(song.id),
                isSelected = selectedIds.contains(song.id),
                selectionMode = selectedIds.isNotEmpty(),
                reducedMotion = reducedMotion,
                accent = accent,
                onClick = { vm.playSongs(songs, idx) },
                onToggleSelect = { vm.toggleSongSelection(song.id) },
                onToggleFavorite = { vm.toggleFavorite(song.id) },
                onPlayNext = { vm.playNextInQueue(song) },
                onAddToPlaylist = { onOpenPlaylistPickerForSongs(listOf(song.id)) },
                onEditMetadata = { onOpenMetadataEditor(song) },
                onShare = { vm.shareSong(song) },
                onDeleteFile = { onConfirmDelete(song) }
            )
        }
    }
}
