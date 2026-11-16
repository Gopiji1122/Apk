package com.aura.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
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
            // Clean Minimal Header with Settings Icon
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${allSongs.size} offline tracks",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            // Minimal Essential Quick Actions (Shuffle All & Favorites)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeShortcutPill(
                        title = "Shuffle All",
                        subtitle = "${allSongs.size} tracks",
                        icon = Icons.Rounded.Shuffle,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (allSongs.isNotEmpty()) {
                                vm.playSongs(allSongs, allSongs.indices.random(), shuffle = true)
                            }
                        }
                    )
                    HomeShortcutPill(
                        title = "Favorites",
                        subtitle = "${favoriteSongs.size} liked",
                        icon = Icons.Rounded.Favorite,
                        accent = Color(0xFFF43F5E),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onOpenSongListDetail("Favorite Tracks", "${favoriteSongs.size} liked songs", favoriteSongs, null)
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
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn("Tracks", "${allSongs.size}", accent)
                        StatColumn("Artists", "$uniqueArtistsCount", accent)
                        StatColumn("Albums", "$uniqueAlbumsCount", accent)
                        StatColumn("Total Time", LyricsAndMetadataHelper.formatDuration(totalDurationMs), accent)
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
                            .clip(RoundedCornerShape(18.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .clickable { onOpenNowPlaying() }
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
                                color = accent
                            )
                            Text(active.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${active.artist} • ${active.album}",
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
                    HomeSectionHeader("Recently Played", "See All") {
                        onOpenSongListDetail("Recently Played", "${recentSongs.size} recent tracks", recentSongs, null)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(recentSongs) { idx, song ->
                            HomeSongCard(song = song, accent = accent, badge = null) {
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
                    HomeSectionHeader("Favorites", "See All") {
                        onOpenSongListDetail("Favorite Tracks", "${favoriteSongs.size} liked songs", favoriteSongs, null)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(favoriteSongs.take(12)) { idx, song ->
                            HomeSongCard(song = song, accent = accent, badge = null) {
                                vm.playSongs(favoriteSongs, idx)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Optional Recently Added Carousel (OFF by default; enabled in Settings)
            if (settings.homeSections.showRecentlyAdded && recentlyAddedSongs.isNotEmpty()) {
                item {
                    HomeSectionHeader("Recently Added", "See All") {
                        onOpenSongListDetail("Recently Added", "Latest audio files on device", recentlyAddedSongs, null)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(recentlyAddedSongs) { idx, song ->
                            HomeSongCard(song = song, accent = accent, badge = "NEW") {
                                vm.playSongs(recentlyAddedSongs, idx)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Optional Most Played Carousel (OFF by default; enabled in Settings)
            if (settings.homeSections.showMostPlayed && mostPlayedSongs.isNotEmpty()) {
                item {
                    HomeSectionHeader("Most Played", "See All") {
                        onOpenSongListDetail("Most Played", "Your top tracks", mostPlayedSongs, null)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(mostPlayedSongs) { idx, song ->
                            val count = playCounts[song.id] ?: 0
                            HomeSongCard(song = song, accent = accent, badge = "${count}x") {
                                vm.playSongs(mostPlayedSongs, idx)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // Optional Playlists Row (OFF by default; enabled in Settings)
            if (settings.homeSections.showPlaylists && playlists.isNotEmpty()) {
                item {
                    HomeSectionHeader("Your Playlists", "See All") {
                        onNavigateTab(MainDestination.PLAYLISTS)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists) { playlist ->
                            val pSongs = playlist.songIds.mapNotNull { songMap[it] }
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        onOpenSongListDetail(
                                            playlist.name,
                                            "${pSongs.size} tracks",
                                            pSongs,
                                            playlist.id
                                        )
                                    }
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

            // Clean Recent/Quick Track List on Home
            if (allSongs.isNotEmpty()) {
                item {
                    HomeSectionHeader("Quick Play", "Library") {
                        onNavigateTab(MainDestination.LIBRARY)
                    }
                }
                itemsIndexed(allSongs.take(15), key = { _, s -> s.id }) { idx, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { vm.playSongs(allSongs, idx) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtView(
                            song = song,
                            sizePx = 128,
                            accent = accent,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                color = if (currentSong?.id == song.id) accent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                song.artist,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            LyricsAndMetadataHelper.formatDuration(song.duration),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (isScanning) "Scanning local storage..." else "Pull down to scan offline audio files.",
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
private fun HomeShortcutPill(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = accent)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAction) {
            Text(actionLabel, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeSongCard(
    song: Song,
    accent: Color,
    badge: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Box(modifier = Modifier.size(112.dp)) {
            AlbumArtView(
                song = song,
                sizePx = 220,
                accent = accent,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(badge, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
