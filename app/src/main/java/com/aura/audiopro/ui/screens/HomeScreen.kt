package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.model.*
import com.aura.audiopro.ui.components.AlbumArtView
import com.aura.audiopro.ui.components.ProPillButton
import com.aura.audiopro.ui.components.ProSectionTitle
import com.aura.audiopro.ui.components.SongListItem
import com.aura.audiopro.ui.components.bounceClick
import com.aura.audiopro.ui.viewmodel.AuraViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: AuraViewModel,
    accent: Color,
    onOpenNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateTab: (MainDestination) -> Unit,
    onOpenSongListDetail: (String, String, List<Song>, Long?) -> Unit
) {
    val allSongs by vm.allSongs.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val recentIds by vm.recentlyPlayedIds.collectAsState()
    val playCounts by vm.playHistoryMap.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    val isScanning by vm.isScanning.collectAsState()

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

    val songMap = remember(allSongs) { allSongs.associateBy { it.id } }
    val recentSongs = remember(recentIds, songMap) { recentIds.mapNotNull { songMap[it] }.take(15) }
    val recentlyAddedSongs = remember(allSongs) { allSongs.sortedByDescending { it.dateAdded }.take(15) }
    val mostPlayedSongs = remember(allSongs, playCounts) {
        allSongs.filter { (playCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { playCounts[it.id] ?: 0 }
            .take(15)
    }
    val favoriteSongs = remember(allSongs, favoriteIds) {
        allSongs.filter { favoriteIds.contains(it.id) }
    }

    val totalDurationMs = remember(allSongs) { allSongs.sumOf { it.duration } }
    val uniqueArtistsCount = remember(allSongs) { allSongs.map { it.artist.lowercase() }.distinct().size }
    val uniqueAlbumsCount = remember(allSongs) { allSongs.map { it.album.lowercase() }.distinct().size }

    PullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = { vm.rescanLibrary() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // ProMusicPlayer Header with Settings Icon
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${allSongs.size} songs on device",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .bounceClick(scaleDown = 0.90f, onClick = onOpenSettings)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // ProMusicPlayer Pill Action Buttons Row (Shuffle All & Liked Songs)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProPillButton(
                        text = "Shuffle All",
                        icon = Icons.Rounded.Shuffle,
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (allSongs.isNotEmpty()) {
                                vm.playSongs(allSongs, allSongs.indices.random(), shuffle = true)
                            }
                        }
                    )
                    ProPillButton(
                        text = "Liked (${favoriteSongs.size})",
                        icon = Icons.Rounded.Favorite,
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onOpenSongListDetail("Liked Songs", "${favoriteSongs.size} favorite tracks", favoriteSongs, null)
                        }
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            // Optional Statistics (OFF by default; enabled in Settings)
            if (settings.homeSections.showStats && allSongs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn("Tracks", "${allSongs.size}", accent)
                        StatColumn("Artists", "$uniqueArtistsCount", accent)
                        StatColumn("Albums", "$uniqueAlbumsCount", accent)
                        StatColumn("Duration", LyricsAndMetadataHelper.formatDuration(totalDurationMs), accent)
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Optional Currently Playing Banner (OFF by default; enabled in Settings)
            if (settings.homeSections.showCurrentlyPlaying && currentSong != null) {
                item {
                    val active = currentSong!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .bounceClick(scaleDown = 0.97f, onClick = onOpenNowPlaying)
                            .clip(RoundedCornerShape(18.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtView(
                            song = active,
                            sizePx = 180,
                            accent = accent,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (isPlaying) "NOW PLAYING" else "PAUSED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                letterSpacing = 1.2.sp
                            )
                            Text(active.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${active.artist}  |  ${active.album}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { vm.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                                contentDescription = "Play/Pause",
                                tint = accent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Recently Played Carousel (ON by default)
            if (settings.homeSections.showRecentlyPlayed && recentSongs.isNotEmpty()) {
                item {
                    ProSectionTitle(
                        title = "Recently played",
                        action = "See all",
                        onAction = {
                            onOpenSongListDetail("Recently Played", "${recentSongs.size} recent tracks", recentSongs, null)
                        }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(recentSongs, key = { _, s -> "rec_${s.id}" }) { idx, song ->
                            ProSongCarouselCard(song = song, accent = accent, badge = null) {
                                vm.playSongs(recentSongs, idx)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Favorites Carousel (ON by default)
            if (settings.homeSections.showFavorites && favoriteSongs.isNotEmpty()) {
                item {
                    ProSectionTitle(
                        title = "Liked songs",
                        action = "See all",
                        onAction = {
                            onOpenSongListDetail("Liked Songs", "${favoriteSongs.size} liked songs", favoriteSongs, null)
                        }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(favoriteSongs.take(12), key = { _, s -> "fav_${s.id}" }) { idx, song ->
                            ProSongCarouselCard(song = song, accent = accent, badge = null) {
                                vm.playSongs(favoriteSongs, idx)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Optional Most Played Carousel (OFF by default; enabled in Settings)
            if (settings.homeSections.showMostPlayed && mostPlayedSongs.isNotEmpty()) {
                item {
                    ProSectionTitle(
                        title = "Most played",
                        action = "See all",
                        onAction = {
                            onOpenSongListDetail("Most Played", "Your top tracks", mostPlayedSongs, null)
                        }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(mostPlayedSongs, key = { _, s -> "most_${s.id}" }) { idx, song ->
                            val count = playCounts[song.id] ?: 0
                            ProSongCarouselCard(song = song, accent = accent, badge = "${count}x") {
                                vm.playSongs(mostPlayedSongs, idx)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Optional Recently Added Carousel (OFF by default; enabled in Settings)
            if (settings.homeSections.showRecentlyAdded && recentlyAddedSongs.isNotEmpty()) {
                item {
                    ProSectionTitle(
                        title = "Recently added",
                        action = "See all",
                        onAction = {
                            onOpenSongListDetail("Recently Added", "Latest audio files on device", recentlyAddedSongs, null)
                        }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(recentlyAddedSongs, key = { _, s -> "add_${s.id}" }) { idx, song ->
                            ProSongCarouselCard(song = song, accent = accent, badge = "NEW") {
                                vm.playSongs(recentlyAddedSongs, idx)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Optional Playlists Row (OFF by default; enabled in Settings)
            if (settings.homeSections.showPlaylists && playlists.isNotEmpty()) {
                item {
                    ProSectionTitle(
                        title = "Playlists",
                        action = "See all",
                        onAction = { onNavigateTab(MainDestination.PLAYLISTS) }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists, key = { "pl_${it.id}" }) { playlist ->
                            val pSongs = playlist.songIds.mapNotNull { songMap[it] }
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .bounceClick(scaleDown = 0.95f) {
                                        onOpenSongListDetail(
                                            playlist.name,
                                            "${pSongs.size} tracks",
                                            pSongs,
                                            playlist.id
                                        )
                                    }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(14.dp)
                            ) {
                                Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(playlist.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${pSongs.size} songs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Quick Songs List on Home
            if (allSongs.isNotEmpty()) {
                item {
                    ProSectionTitle(
                        title = "All songs",
                        action = "Library",
                        onAction = { onNavigateTab(MainDestination.LIBRARY) }
                    )
                }
                itemsIndexed(allSongs.take(20), key = { _, s -> s.id }) { idx, song ->
                    SongListItem(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isPlaying = isPlaying,
                        isFavorite = favoriteIds.contains(song.id),
                        isSelected = false,
                        selectionMode = false,
                        reducedMotion = settings.reducedMotion,
                        accent = accent,
                        onClick = { vm.playSongs(allSongs, idx) },
                        onToggleSelect = {},
                        onToggleFavorite = { vm.toggleFavorite(song.id) },
                        onPlayNext = { vm.playNextInQueue(song) },
                        onAddToPlaylist = {},
                        onEditMetadata = {},
                        onShare = { vm.shareSong(song) }
                    )
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(42.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (isScanning) "Scanning local storage..." else "No music found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Pull down to scan offline audio files on your phone.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = accent)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProSongCarouselCard(
    song: Song,
    accent: Color,
    badge: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(138.dp)
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
    ) {
        Box(modifier = Modifier.size(118.dp)) {
            AlbumArtView(
                song = song,
                sizePx = 240,
                accent = accent,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(badge, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = song.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = song.artist,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
