package com.promusic.player

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application = app
    val store = Store(app)

    // ---------- library ----------
    var hasPermission by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var folders by mutableStateOf<List<FolderItem>>(emptyList())
        private set
    private var byId: Map<Long, Song> = emptyMap()

    var favorites by mutableStateOf<Set<Long>>(store.getLongs("favorites").toSet())
        private set
    var playlists by mutableStateOf<Map<String, List<Long>>>(store.playlists())
        private set
    var playCounts by mutableStateOf<Map<Long, Int>>(store.playCounts())
        private set
    var recents by mutableStateOf<List<Long>>(store.getLongs("recents"))
        private set

    // ---------- player state ----------
    var current by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var queueIndex by mutableIntStateOf(-1)
        private set

    // ---------- UI state ----------
    var tab by mutableIntStateOf(0)
    var libraryTab by mutableIntStateOf(0)
    var showPlayer by mutableStateOf(false)
    var showQueue by mutableStateOf(false)
    var showSleep by mutableStateOf(false)
    var showEq by mutableStateOf(false)
    var playlistTarget by mutableStateOf<Song?>(null)
    var detailStack by mutableStateOf<List<Detail>>(emptyList())
        private set

    // ---------- settings ----------
    var accentIndex by mutableIntStateOf(store.getInt("accent", 0))
        private set
    var amoled by mutableStateOf(store.getBool("amoled", false))
        private set
    var minDurationSec by mutableIntStateOf(store.getInt("min_sec", 30))
        private set
    var sortMode by mutableIntStateOf(store.getInt("sort", 0))
        private set

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var restored = false
    private var lastCounted = -1L
    private var lastScan = 0L

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerViewModel.isPlaying = isPlaying
            if (isPlaying) countPlay() else saveState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastCounted = -1L
            syncCurrent()
            if (controller?.playWhenReady == true) countPlay()
            saveState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            syncQueue()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffle = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@PlayerViewModel.repeatMode = repeatMode
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            speed = playbackParameters.speed
        }
    }

    init {
        AudioFx.init(app)
        checkPermission()
        connect()
        if (hasPermission) load()
    }

    // ---------- permission & scanning ----------
    fun checkPermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        hasPermission = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult() {
        checkPermission()
        if (hasPermission) load()
    }

    fun onAppStart() {
        checkPermission()
        if (hasPermission && SystemClock.elapsedRealtime() - lastScan > 30_000L) {
            load(silent = songs.isNotEmpty())
        }
    }

    fun load(silent: Boolean = false) {
        if (!hasPermission) return
        viewModelScope.launch {
            if (!silent) loading = true
            val minMs = minDurationSec * 1000L
            val result = withContext(Dispatchers.IO) {
                try {
                    scanDeviceMusic(ctx, minMs)
                } catch (t: Throwable) {
                    null
                }
            }
            lastScan = SystemClock.elapsedRealtime()
            if (result != null && (!silent || result != songs)) setLibrary(result)
            loading = false
        }
    }

    private fun setLibrary(list: List<Song>) {
        songs = list
        byId = list.associateBy { it.id }
        albums = list.groupBy { it.albumId }.map { (id, ss) ->
            val artistsInAlbum = ss.map { it.artist }.distinct()
            Album(
                id,
                ss.first().album,
                if (artistsInAlbum.size == 1) artistsInAlbum[0] else "Various artists",
                ss.sortedBy { it.track }
            )
        }.sortedBy { it.name.lowercase() }
        artists = list.groupBy { it.artist }
            .map { (name, ss) -> Artist(name, ss) }
            .sortedBy { it.name.lowercase() }
        folders = list.groupBy { it.folderPath }
            .map { (path, ss) -> FolderItem(path, ss.first().folderName, ss) }
            .sortedBy { it.name.lowercase() }
        syncCurrent()
        syncQueue()
        tryRestore()
    }

    fun songById(id: Long): Song? = byId[id]

    fun sortedSongs(): List<Song> = when (sortMode) {
        1 -> songs.sortedBy { it.artist.lowercase() }
        2 -> songs.sortedByDescending { it.dateAdded }
        3 -> songs.sortedByDescending { it.durationMs }
        4 -> songs.sortedByDescending { playCounts[it.id] ?: 0 }
        else -> songs
    }

    fun songsFor(d: Detail): List<Song> = when (d.type) {
        DetailType.ALBUM -> albums.firstOrNull { it.id.toString() == d.key }?.songs.orEmpty()
        DetailType.ARTIST -> artists.firstOrNull { it.name == d.key }?.songs.orEmpty()
        DetailType.FOLDER -> folders.firstOrNull { it.path == d.key }?.songs.orEmpty()
        DetailType.PLAYLIST -> playlists[d.key].orEmpty().mapNotNull { byId[it] }
        DetailType.LIKED -> songs.filter { it.id in favorites }
        DetailType.RECENT -> recents.mapNotNull { byId[it] }
        DetailType.MOST_PLAYED -> songs.filter { (playCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { playCounts[it.id] ?: 0 }.take(100)
        DetailType.ADDED -> songs.sortedByDescending { it.dateAdded }.take(100)
    }

    fun titleFor(d: Detail): String = when (d.type) {
        DetailType.ALBUM -> albums.firstOrNull { it.id.toString() == d.key }?.name ?: "Album"
        DetailType.ARTIST -> d.key
        DetailType.FOLDER -> folders.firstOrNull { it.path == d.key }?.name ?: "Folder"
        DetailType.PLAYLIST -> d.key
        DetailType.LIKED -> "Liked songs"
        DetailType.RECENT -> "Recently played"
        DetailType.MOST_PLAYED -> "Most played"
        DetailType.ADDED -> "Recently added"
    }

    // ---------- navigation ----------
    fun openDetail(d: Detail) {
        showPlayer = false
        detailStack = detailStack + d
    }

    fun clearDetails() {
        detailStack = emptyList()
    }

    fun backDetail() {
        if (detailStack.isNotEmpty()) detailStack = detailStack.dropLast(1)
    }

    // ---------- settings ----------
    fun updateAccent(i: Int) { accentIndex = i; store.putInt("accent", i) }
    fun updateAmoled(v: Boolean) { amoled = v; store.putBool("amoled", v) }
    fun updateSort(i: Int) { sortMode = i; store.putInt("sort", i) }
    fun updateMinDuration(sec: Int) {
        minDurationSec = sec
        store.putInt("min_sec", sec)
        load()
    }

    // ---------- controller ----------
    private fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                isPlaying = c.isPlaying
                shuffle = c.shuffleModeEnabled
                repeatMode = c.repeatMode
                speed = c.playbackParameters.speed
                syncCurrent()
                syncQueue()
                tryRestore()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun idOf(item: MediaItem?): Long? = item?.mediaId?.toLongOrNull()

    private fun syncCurrent() {
        val c = controller ?: return
        current = idOf(c.currentMediaItem)?.let { byId[it] }
        queueIndex = c.currentMediaItemIndex
    }

    private fun syncQueue() {
        val c = controller ?: return
        queue = (0 until c.mediaItemCount).mapNotNull { i -> idOf(c.getMediaItemAt(i))?.let { byId[it] } }
        queueIndex = c.currentMediaItemIndex
    }

    private fun tryRestore() {
        val c = controller ?: return
        if (restored || byId.isEmpty()) return
        restored = true
        if (c.mediaItemCount > 0) return
        val items = store.getLongs("q_ids").mapNotNull { byId[it] }
        if (items.isEmpty()) return
        val cur = store.getLong("q_cur", -1L)
        val idx = items.indexOfFirst { it.id == cur }.coerceAtLeast(0)
        c.shuffleModeEnabled = store.getBool("shuffle", false)
        c.repeatMode = store.getInt("repeat", Player.REPEAT_MODE_OFF)
        c.setMediaItems(items.map { it.toMediaItem() }, idx, store.getLong("q_pos", 0L))
        c.prepare()
    }

    fun saveState() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val ids = (0 until c.mediaItemCount).mapNotNull { idOf(c.getMediaItemAt(it)) }
        store.putLongs("q_ids", ids)
        store.putLong("q_cur", idOf(c.currentMediaItem) ?: -1L)
        store.putLong("q_pos", c.currentPosition)
        store.putBool("shuffle", c.shuffleModeEnabled)
        store.putInt("repeat", c.repeatMode)
    }

    private fun countPlay() {
        val s = current ?: return
        if (lastCounted == s.id) return
        lastCounted = s.id
        playCounts = playCounts + (s.id to ((playCounts[s.id] ?: 0) + 1))
        store.putPlayCounts(playCounts)
        recents = (listOf(s.id) + recents.filter { it != s.id }).take(50)
        store.putLongs("recents", recents)
    }

    // ---------- playback actions ----------
    fun play(list: List<Song>, index: Int, shuffleOn: Boolean? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        var l = list
        var idx = index.coerceIn(0, list.lastIndex)
        if (l.size > 2000) {
            val start = (idx - 1000).coerceAtLeast(0)
            l = l.subList(start, minOf(l.size, start + 2000))
            idx -= start
        }
        if (shuffleOn != null) c.shuffleModeEnabled = shuffleOn
        c.setMediaItems(l.map { it.toMediaItem() }, idx, 0L)
        c.prepare()
        c.play()
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isNotEmpty()) play(list, list.indices.random(), true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun changeSpeed(s: Float) { controller?.setPlaybackSpeed(s) }

    fun playNext(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(s), 0)
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, s.toMediaItem())
        toast("Will play next")
    }

    fun addToQueue(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(s), 0)
            return
        }
        c.addMediaItem(s.toMediaItem())
        toast("Added to queue")
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    // ---------- favorites & playlists ----------
    fun toggleFavorite(s: Song) {
        favorites = if (s.id in favorites) favorites - s.id else favorites + s.id
        store.putLongs("favorites", favorites.toList())
    }

    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isEmpty() || playlists.containsKey(n)) return
        val m = LinkedHashMap(playlists)
        m[n] = emptyList()
        playlists = m
        store.putPlaylists(m)
    }

    fun deletePlaylist(name: String) {
        val m = LinkedHashMap(playlists)
        m.remove(name)
        playlists = m
        store.putPlaylists(m)
    }

    fun addToPlaylist(name: String, s: Song) {
        val cur = playlists[name] ?: return
        if (s.id in cur) {
            toast("Already in $name")
            return
        }
        val m = LinkedHashMap(playlists)
        m[name] = cur + s.id
        playlists = m
        store.putPlaylists(m)
        toast("Added to $name")
    }

    fun removeFromPlaylist(name: String, s: Song) {
        val cur = playlists[name] ?: return
        val m = LinkedHashMap(playlists)
        m[name] = cur.filter { it != s.id }
        playlists = m
        store.putPlaylists(m)
    }

    fun toast(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        saveState()
        future?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
