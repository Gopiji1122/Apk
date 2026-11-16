package com.promusic.player

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ======================= HOME =======================

@Composable
fun HomeScreen(vm: PlayerViewModel) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val recent = vm.recents.mapNotNull { vm.songById(it) }.take(20)
    val most = remember(vm.playCounts, vm.songs) { vm.songsFor(Detail(DetailType.MOST_PLAYED, "")).take(20) }
    val added = remember(vm.songs) { vm.songsFor(Detail(DetailType.ADDED, "")).take(20) }
    val liked = remember(vm.favorites, vm.songs) { vm.songsFor(Detail(DetailType.LIKED, "")).take(20) }

    if (vm.loading && vm.songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (vm.songs.isEmpty()) {
        EmptyState(
            Icons.Rounded.MusicNote,
            "No music found",
            "Add songs to your phone, then open Library and tap refresh. Songs shorter than ${vm.minDurationSec}s are hidden."
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text(greeting, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${vm.songs.size} songs  |  ${vm.albums.size} albums  |  ${vm.artists.size} artists",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PillButton("Shuffle all", Icons.Rounded.Shuffle, true, Modifier.weight(1f)) {
                    vm.shuffleAll(vm.songs)
                }
                PillButton("Play all", Icons.Rounded.PlayArrow, false, Modifier.weight(1f)) {
                    vm.play(vm.songs, 0, false)
                }
            }
        }
        if (recent.isNotEmpty()) item { SongCarousel(vm, "Recently played", recent, Detail(DetailType.RECENT, "")) }
        if (most.isNotEmpty()) item { SongCarousel(vm, "Most played", most, Detail(DetailType.MOST_PLAYED, "")) }
        if (liked.isNotEmpty()) item { SongCarousel(vm, "Liked songs", liked, Detail(DetailType.LIKED, "")) }
        item { SongCarousel(vm, "Recently added", added, Detail(DetailType.ADDED, "")) }
    }
}

@Composable
fun SongCarousel(vm: PlayerViewModel, title: String, list: List<Song>, more: Detail) {
    SectionTitle(title, "See all") { vm.openDetail(more) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            Column(
                Modifier
                    .width(140.dp)
                    .bounceClick { vm.play(list, i) }
            ) {
                ArtImage(s, Modifier.size(140.dp), 320, RoundedCornerShape(18.dp), 40.dp)
                Spacer(Modifier.height(8.dp))
                Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    s.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ======================= LIBRARY =======================

@Composable
fun LibraryScreen(vm: PlayerViewModel) {
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists", "Folders")
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScreenTitle("Library", Modifier.weight(1f))
            IconButton(onClick = { vm.load() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan library")
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(tabs) { i, t -> Pill(t, vm.libraryTab == i) { vm.libraryTab = i } }
        }
        AnimatedContent(
            targetState = vm.libraryTab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "libtab"
        ) { t ->
            when (t) {
                0 -> SongsTab(vm)
                1 -> AlbumsTab(vm)
                2 -> ArtistsTab(vm)
                3 -> PlaylistsTab(vm)
                else -> FoldersTab(vm)
            }
        }
    }
}

@Composable
fun SongsTab(vm: PlayerViewModel) {
    val list = remember(vm.songs, vm.sortMode, vm.playCounts) { vm.sortedSongs() }
    val sorts = listOf("Title", "Artist", "Recently added", "Duration", "Most played")
    var menu by remember { mutableStateOf(false) }
    if (list.isEmpty()) {
        EmptyState(Icons.Rounded.LibraryMusic, "Nothing here yet", "Your songs will show up here.")
        return
    }
    SongList(vm, list, header = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(sorts[vm.sortMode.coerceIn(0, sorts.lastIndex)])
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    sorts.forEachIndexed { i, s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { menu = false; vm.updateSort(i) })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.shuffleAll(list) }) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = MaterialTheme.colorScheme.primary)
            }
        }
    })
}

@Composable
fun AlbumsTab(vm: PlayerViewModel) {
    if (vm.albums.isEmpty()) {
        EmptyState(Icons.Rounded.LibraryMusic, "No albums", "Albums are grouped from your song tags.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(vm.albums, key = { it.id }) { al ->
            Column(Modifier.bounceClick { vm.openDetail(Detail(DetailType.ALBUM, al.id.toString())) }) {
                ArtImage(
                    al.songs.first(),
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    400,
                    RoundedCornerShape(18.dp),
                    44.dp
                )
                Spacer(Modifier.height(8.dp))
                Text(al.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    al.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ArtistsTab(vm: PlayerViewModel) {
    if (vm.artists.isEmpty()) {
        EmptyState(Icons.Rounded.LibraryMusic, "No artists", "Artists are read from your song tags.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        items(vm.artists, key = { it.name }) { a ->
            InfoRow(
                title = a.name,
                subtitle = "${a.songs.size} songs  |  ${a.albumCount} albums",
                onClick = { vm.openDetail(Detail(DetailType.ARTIST, a.name)) },
                leading = { ArtImage(a.songs.first(), Modifier.size(52.dp), 128, CircleShape) }
            )
        }
    }
}

@Composable
fun FoldersTab(vm: PlayerViewModel) {
    if (vm.folders.isEmpty()) {
        EmptyState(Icons.Rounded.Folder, "No folders", "Folders that contain music appear here.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        items(vm.folders, key = { it.path }) { f ->
            InfoRow(
                title = f.name,
                subtitle = "${f.songs.size} songs  |  ${f.path}",
                onClick = { vm.openDetail(Detail(DetailType.FOLDER, f.path)) },
                leading = { IconBox(Icons.Rounded.Folder) }
            )
        }
    }
}

@Composable
fun PlaylistsTab(vm: PlayerViewModel) {
    var showNew by remember { mutableStateOf(false) }
    val smart = listOf(
        Triple("Liked songs", Icons.Rounded.Favorite, DetailType.LIKED),
        Triple("Recently played", Icons.Rounded.History, DetailType.RECENT),
        Triple("Most played", Icons.Rounded.TrendingUp, DetailType.MOST_PLAYED),
        Triple("Recently added", Icons.Rounded.Schedule, DetailType.ADDED)
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        item {
            Box(Modifier.padding(start = 8.dp, bottom = 10.dp)) {
                PillButton("New playlist", Icons.Rounded.Add, true) { showNew = true }
            }
        }
        items(smart, key = { it.first }) { (name, icon, type) ->
            val count = vm.songsFor(Detail(type, "")).size
            InfoRow(
                title = name,
                subtitle = "$count songs",
                onClick = { vm.openDetail(Detail(type, "")) },
                leading = { IconBox(icon) }
            )
        }
        item {
            if (vm.playlists.isNotEmpty()) {
                Text(
                    "Your playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp)
                )
            }
        }
        items(vm.playlists.keys.toList(), key = { "pl_$it" }) { name ->
            val ids = vm.playlists[name].orEmpty()
            InfoRow(
                title = name,
                subtitle = "${ids.size} songs",
                onClick = { vm.openDetail(Detail(DetailType.PLAYLIST, name)) },
                leading = { IconBox(Icons.Rounded.QueueMusic) },
                trailing = {
                    IconButton(onClick = { vm.deletePlaylist(name) }) {
                        Icon(Icons.Rounded.Delete, "Delete playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
    if (showNew) {
        NewPlaylistDialog(onDismiss = { showNew = false }, onCreate = { vm.createPlaylist(it) })
    }
}

// ======================= DETAIL =======================

@Composable
fun DetailScreen(vm: PlayerViewModel, detail: Detail) {
    val list = remember(detail, vm.songs, vm.playlists, vm.favorites, vm.recents, vm.playCounts) {
        vm.songsFor(detail)
    }
    val total = list.sumOf { it.durationMs }
    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.backDetail() }) { Icon(Icons.Rounded.ArrowBack, "Back") }
            }
            SongList(
                vm, list,
                header = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ArtImage(list.firstOrNull(), Modifier.size(200.dp), 512, RoundedCornerShape(24.dp), 56.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            vm.titleFor(detail),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${list.size} songs  |  ${fmtTotal(total)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        if (list.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PillButton("Play", Icons.Rounded.PlayArrow, true) { vm.play(list, 0, false) }
                                PillButton("Shuffle", Icons.Rounded.Shuffle, false) { vm.shuffleAll(list) }
                            }
                        }
                    }
                },
                onRemove = if (detail.type == DetailType.PLAYLIST) ({ s: Song -> vm.removeFromPlaylist(detail.key, s) }) else null
            )
        }
    }
}

// ======================= SEARCH =======================

@Composable
fun SearchScreen(vm: PlayerViewModel) {
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val songs = remember(query, vm.songs) {
        if (query.isEmpty()) emptyList() else vm.songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }
    val albums = remember(query, vm.albums) {
        if (query.isEmpty()) emptyList() else vm.albums.filter {
            it.name.contains(query, true) || it.artist.contains(query, true)
        }.take(5)
    }
    val artists = remember(query, vm.artists) {
        if (query.isEmpty()) emptyList() else vm.artists.filter { it.name.contains(query, true) }.take(5)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Search")
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("Songs, artists, albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (q.isNotEmpty()) {
                    IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))
        when {
            query.isEmpty() -> EmptyState(Icons.Rounded.Search, "Find your music", "Search by song title, artist or album.")
            songs.isEmpty() && albums.isEmpty() && artists.isEmpty() ->
                EmptyState(Icons.Rounded.Search, "No results", "Nothing matches \"$query\". Check the spelling or try another word.")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                if (artists.isNotEmpty()) {
                    item { SectionTitle("Artists") }
                    items(artists, key = { "ar_" + it.name }) { a ->
                        InfoRow(
                            a.name, "${a.songs.size} songs",
                            { vm.openDetail(Detail(DetailType.ARTIST, a.name)) },
                            { ArtImage(a.songs.first(), Modifier.size(52.dp), 128, CircleShape) }
                        )
                    }
                }
                if (albums.isNotEmpty()) {
                    item { SectionTitle("Albums") }
                    items(albums, key = { "al_" + it.id }) { al ->
                        InfoRow(
                            al.name, al.artist,
                            { vm.openDetail(Detail(DetailType.ALBUM, al.id.toString())) },
                            { ArtImage(al.songs.first(), Modifier.size(52.dp), 128, RoundedCornerShape(10.dp)) }
                        )
                    }
                }
                if (songs.isNotEmpty()) {
                    item { SectionTitle("Songs") }
                    itemsIndexed(songs, key = { _, s -> "s_" + s.id }) { i, s ->
                        SongRow(vm, s, vm.current?.id == s.id, onClick = { vm.play(songs, i) })
                    }
                }
            }
        }
    }
}

// ======================= SETTINGS =======================

@Composable
fun SettingsScreen(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ScreenTitle("Settings")

        GroupTitle("Appearance")
        SettingsCard {
            Text("Accent colour", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Accents.forEachIndexed { i, (_, c) ->
                    val sel = vm.accentIndex == i
                    Box(
                        Modifier
                            .size(40.dp)
                            .bounceClick { vm.updateAccent(i) }
                            .colorDot(c, sel),
                        contentAlignment = Alignment.Center
                    ) {
                        if (sel) Icon(Icons.Rounded.Check, null, tint = Color.Black)
                    }
                }
            }
            SettingRow(
                Icons.Rounded.MusicNote, "Pure black (AMOLED)", "Saves battery on OLED screens",
                onClick = { vm.updateAmoled(!vm.amoled) }
            ) {
                Switch(
                    checked = vm.amoled,
                    onCheckedChange = { vm.updateAmoled(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = Color.Black
                    )
                )
            }
        }

        GroupTitle("Audio")
        SettingsCard {
            SettingRow(Icons.Rounded.Equalizer, "Equalizer", "5-band EQ, presets and bass boost", onClick = { vm.showEq = true })
            SettingRow(
                Icons.Rounded.Bedtime, "Sleep timer",
                if (SleepTimer.endOfTrack) "Stops after this song" else if (SleepTimer.endsAt > 0L) "Active" else "Off",
                onClick = { vm.showSleep = true }
            )
        }

        GroupTitle("Library")
        SettingsCard {
            Text("Hide short tracks", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            Text(
                "Skips voice notes and ringtones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            )
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "Show all", 30 to "Under 30s", 60 to "Under 60s").forEach { (sec, label) ->
                    Pill(label, vm.minDurationSec == sec) { vm.updateMinDuration(sec) }
                }
            }
            SettingRow(Icons.Rounded.Refresh, "Rescan library", "${vm.songs.size} songs found", onClick = { vm.load() })
        }

        GroupTitle("Permissions")
        SettingsCard {
            SettingRow(Icons.Rounded.LibraryMusic, "App permissions", "Open system settings", onClick = {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    )
                } catch (_: Exception) {
                }
            })
        }

        Text(
            "Aura Audio Player 2.0\n100% offline. No ads, no tracking, no internet permission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        )
    }
}

private fun Modifier.colorDot(color: Color, selected: Boolean): Modifier =
    this
        .clip(CircleShape)
        .background(color)
        .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}
