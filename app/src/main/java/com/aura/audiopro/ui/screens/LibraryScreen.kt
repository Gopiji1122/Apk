package com.aura.audiopro.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.aura.audiopro.ui.components.ProIconBox
import com.aura.audiopro.ui.components.ProPill
import com.aura.audiopro.ui.components.SongListItem
import com.aura.audiopro.ui.components.bounceClick
import com.aura.audiopro.ui.viewmodel.AuraViewModel

@Composable
fun LibraryScreen(
    vm: AuraViewModel,
    accent: Color,
    onOpenSettings: () -> Unit,
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
    val isScanning by vm.isScanning.collectAsState()

    val subTabs = listOf(
        "Songs",
        "Albums",
        "Artists",
        "Liked",
        "Recent",
        "Added",
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
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        Row(
                            modifier = Modifier
                                .bounceClick(scaleDown = 0.94f) { showSortMenu = true }
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sort,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = sortOption.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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
        }

        // ProMusicPlayer Search Field
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
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = accent,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ProMusicPlayer Pill Tabs
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(subTabs) { idx, label ->
                ProPill(
                    text = label,
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx }
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = { vm.rescanLibrary() },
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = selectedSubTab,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "libtab"
            ) { tabIdx ->
                when (tabIdx) {
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
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 152.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(albums, key = { it.key }) { entry ->
                                val first = entry.value.first()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceClick(scaleDown = 0.95f) {
                                            onOpenSongListDetail(
                                                entry.key,
                                                "${first.artist} • ${entry.value.size} songs",
                                                entry.value,
                                                null
                                            )
                                        }
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(10.dp)
                                ) {
                                    AlbumArtView(
                                        song = first,
                                        sizePx = 256,
                                        accent = accent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = entry.key,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${first.artist} • ${entry.value.size} songs",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                            items(artists, key = { it.key }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceClick(scaleDown = 0.97f) {
                                            onOpenSongListDetail(
                                                entry.key,
                                                "${entry.value.size} tracks",
                                                entry.value,
                                                null
                                            )
                                        }
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProIconBox(icon = Icons.Rounded.Person, tint = accent)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.key, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text("${entry.value.size} songs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C9D), contentColor = Color.Black)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (songs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Nothing here yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "Pull down to refresh your offline library.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
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
}
