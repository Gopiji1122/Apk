package com.aura.audiopro.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.audiopro.audio.AuraPlaybackService
import com.aura.audiopro.core.AlbumArtCache
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.core.decodeAlbumBitmap
import com.aura.audiopro.data.db.AuraDatabase
import com.aura.audiopro.data.db.CustomEqPresetEntity
import com.aura.audiopro.data.db.MetadataOverrideEntity
import com.aura.audiopro.data.repository.MusicRepository
import com.aura.audiopro.data.scanner.MediaStoreScanner
import com.aura.audiopro.model.*
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AuraDatabase.getInstance(application)
    private val repository = MusicRepository(application, db.auraDao())
    private val scanner = MediaStoreScanner(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var mediaObserver: ContentObserver? = null

    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _sortOption = MutableStateFlow(repository.loadSortOption())
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _settings = MutableStateFlow(repository.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isMiniPlayerVisible = MutableStateFlow(true)
    val isMiniPlayerVisible: StateFlow<Boolean> = _isMiniPlayerVisible.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _dominantColor = MutableStateFlow(Color(0xFF1DB954))
    val dominantColor: StateFlow<Color> = _dominantColor.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val currentLyrics: StateFlow<List<LrcLine>> = _currentLyrics.asStateFlow()

    private val _currentTechDetails = MutableStateFlow<SongTechDetails?>(null)
    val currentTechDetails: StateFlow<SongTechDetails?> = _currentTechDetails.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _outputDevices = MutableStateFlow<List<AudioOutputDeviceItem>>(emptyList())
    val outputDevices: StateFlow<List<AudioOutputDeviceItem>> = _outputDevices.asStateFlow()

    private val _selectedOutputDeviceId = MutableStateFlow<Int?>(null)
    val selectedOutputDeviceId: StateFlow<Int?> = _selectedOutputDeviceId.asStateFlow()

    val favoriteIds: StateFlow<Set<Long>> = repository.favoritesFlow
        .map { list -> list.map { it.songId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val playHistoryMap: StateFlow<Map<Long, Int>> = repository.playHistoryFlow
        .map { list -> list.associate { it.songId to it.playCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recentlyPlayedIds: StateFlow<List<Long>> = repository.playHistoryFlow
        .map { list -> list.sortedByDescending { it.lastPlayedAt }.map { it.songId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistModel>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val excludedFolders: StateFlow<Set<String>> = repository.excludedFoldersFlow
        .map { list -> list.map { it.folderPath }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val metadataOverrides: StateFlow<Map<Long, MetadataOverrideEntity>> = repository.metadataOverridesFlow
        .map { list -> list.associateBy { it.songId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val customEqPresets: StateFlow<List<CustomEqPresetEntity>> = repository.customEqPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSongs: StateFlow<List<Song>> = combine(
        _rawSongs,
        _sortOption,
        playHistoryMap
    ) { songs, sort, counts ->
        LyricsAndMetadataHelper.sortSongs(songs, sort, counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lastRecordedSongId: Long = -1L

    init {
        connectMediaController()
        startProgressTicker()
        refreshOutputDevices()
        mediaObserver = scanner.registerMediaObserver {
            rescanLibrary()
        }
        viewModelScope.launch {
            combine(excludedFolders, metadataOverrides) { ex, ov -> ex to ov }
                .drop(1)
                .collect {
                    rescanLibrary()
                }
        }
    }

    fun refreshOutputDevices() {
        try {
            val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val mapped = mutableListOf<AudioOutputDeviceItem>()
            val seenNames = mutableSetOf<String>()
            for (d in rawDevices) {
                val (label, isBt) = when (d.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth Audio" to true
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> "Wired Headphones / USB" to false
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker" to false
                    else -> continue
                }
                val productName = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: label
                val displayName = if (isBt && productName != label) "$productName (Bluetooth)" else label
                if (seenNames.add(displayName)) {
                    mapped.add(
                        AudioOutputDeviceItem(
                            id = d.id,
                            name = displayName,
                            typeLabel = label,
                            isBluetooth = isBt,
                            isActive = if (_selectedOutputDeviceId.value != null) {
                                d.id == _selectedOutputDeviceId.value
                            } else {
                                isBt || d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                            }
                        )
                    )
                }
            }
            if (mapped.none { it.isActive } && mapped.isNotEmpty()) {
                val first = mapped.first()
                mapped[0] = first.copy(isActive = true)
            }
            _outputDevices.value = mapped
        } catch (_: Exception) {
        }
    }

    fun selectOutputDevice(device: AudioOutputDeviceItem) {
        _selectedOutputDeviceId.value = device.id
        try {
            val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val target = rawDevices.firstOrNull { it.id == device.id }
            if (target != null) {
                com.aura.audiopro.audio.EqualizerController.activePlayer?.setPreferredAudioDevice(target)
            }
            if (device.isBluetooth) {
                audioManager.isSpeakerphoneOn = false
            } else if (device.typeLabel == "Phone Speaker") {
                audioManager.isSpeakerphoneOn = true
            }
        } catch (_: Exception) {
        }
        refreshOutputDevices()
    }

    fun openSystemAudioOutputPanel() {
        try {
            val ctx = getApplication<Application>()
            val intent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                val btIntent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(btIntent)
            } catch (_: Exception) {
            }
        }
    }

    private fun connectMediaController() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, AuraPlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val mc = future.get()
                controller = mc
                _isPlaying.value = mc.isPlaying
                _isShuffle.value = mc.shuffleModeEnabled
                _repeatMode.value = mc.repeatMode
                mc.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) {
                            _isMiniPlayerVisible.value = true
                            _currentSong.value?.let { recordPlayCount(it.id) }
                        }
                        persistQueue()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        syncActiveSong(mc)
                        _currentSong.value?.let { recordPlayCount(it.id) }
                        persistQueue()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _totalDuration.value = mc.duration.coerceAtLeast(0L)
                        syncActiveSong(mc)
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _isShuffle.value = shuffleModeEnabled
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }
                })
                restoreSavedQueueIfNeeded(mc)
            } catch (_: Exception) {
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            while (true) {
                val mc = controller
                if (mc != null && mc.isPlaying) {
                    _currentPosition.value = mc.currentPosition.coerceAtLeast(0L)
                    _totalDuration.value = mc.duration.coerceAtLeast(_currentSong.value?.duration ?: 0L)
                }
                delay(350L)
            }
        }
    }

    fun rescanLibrary() {
        if (_isScanning.value) return
        _isScanning.value = true
        viewModelScope.launch {
            val minDurationMs = _settings.value.minAudioDurationSec * 1000L
            val scanned = withContext(Dispatchers.IO) {
                scanner.scanAudioFiles(
                    minDurationMs = minDurationMs,
                    excludedFolders = excludedFolders.value,
                    overrides = metadataOverrides.value
                )
            }
            if (scanned.isNotEmpty() || _rawSongs.value.isEmpty()) {
                _rawSongs.value = scanned
            }
            _isScanning.value = false
            controller?.let { restoreSavedQueueIfNeeded(it) }
        }
    }

    private fun restoreSavedQueueIfNeeded(mc: MediaController) {
        val songs = allSongs.value.ifEmpty { _rawSongs.value }
        if (songs.isEmpty()) return
        if (mc.mediaItemCount > 0) {
            syncActiveSong(mc)
            return
        }
        val savedIds = repository.getSavedQueueIds()
        if (savedIds.isEmpty()) return
        val byId = songs.associateBy { it.id }
        val restored = savedIds.mapNotNull { byId[it] }
        if (restored.isEmpty()) return

        val idx = repository.getSavedQueueIndex().coerceIn(0, restored.lastIndex)
        val pos = repository.getSavedQueuePos().coerceAtLeast(0L)
        _queue.value = restored
        val active = restored[idx]
        _currentSong.value = active
        _currentPosition.value = pos
        _totalDuration.value = active.duration
        loadSongExtras(active)

        mc.setMediaItems(restored.map { it.toMediaItem() }, idx, pos)
        mc.prepare()
    }

    private fun syncActiveSong(mc: MediaController) {
        val mediaId = mc.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val song = _queue.value.firstOrNull { it.id == mediaId }
            ?: _rawSongs.value.firstOrNull { it.id == mediaId }
        if (song != null) {
            _currentSong.value = song
            _totalDuration.value = mc.duration.takeIf { it > 0 } ?: song.duration
            loadSongExtras(song)
        }
    }

    private fun loadSongExtras(song: Song) {
        val cachedColor = AlbumArtCache.getDominantColor(song.albumId)
        if (cachedColor != null) {
            _dominantColor.value = Color(cachedColor)
        }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            decodeAlbumBitmap(ctx, song.uri, song.albumArtUri, song.albumId, 256)
            AlbumArtCache.getDominantColor(song.albumId)?.let {
                _dominantColor.value = Color(it)
            }
            val lrc = withContext(Dispatchers.IO) {
                val customLrc = metadataOverrides.value[song.id]?.customLyrics.orEmpty()
                if (customLrc.isNotBlank()) {
                    LyricsAndMetadataHelper.parseLrcText(customLrc)
                } else {
                    LyricsAndMetadataHelper.loadLocalLrcForSong(song)
                }
            }
            _currentLyrics.value = lrc
            val tech = withContext(Dispatchers.IO) {
                LyricsAndMetadataHelper.inspectSongTechDetails(ctx, song)
            }
            _currentTechDetails.value = tech
        }
    }

    private fun recordPlayCount(songId: Long) {
        if (lastRecordedSongId == songId) return
        lastRecordedSongId = songId
        viewModelScope.launch {
            repository.recordPlay(songId)
        }
    }

    private fun persistQueue() {
        val mc = controller ?: return
        val currentQ = _queue.value
        if (currentQ.isEmpty()) return
        val idx = mc.currentMediaItemIndex.coerceIn(0, currentQ.lastIndex)
        repository.saveQueueState(currentQ.map { it.id }, idx, mc.currentPosition.coerceAtLeast(0L))
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val mc = controller ?: return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        _queue.value = songs
        val song = songs[idx]
        _currentSong.value = song
        _isMiniPlayerVisible.value = true
        _totalDuration.value = song.duration
        loadSongExtras(song)

        mc.shuffleModeEnabled = shuffle
        _isShuffle.value = shuffle
        mc.setMediaItems(songs.map { it.toMediaItem() }, idx, 0L)
        mc.prepare()
        mc.play()
        recordPlayCount(song.id)
        persistQueue()
    }

    fun dismissMiniPlayer() {
        controller?.pause()
        _isPlaying.value = false
        _isMiniPlayerVisible.value = false
    }

    fun togglePlayPause() {
        val mc = controller ?: return
        if (mc.mediaItemCount == 0 && allSongs.value.isNotEmpty()) {
            playSongs(allSongs.value, 0)
            return
        }
        _isMiniPlayerVisible.value = true
        if (mc.isPlaying) mc.pause() else mc.play()
    }

    fun skipNext() {
        controller?.seekToNext()
    }

    fun skipPrevious() {
        val mc = controller ?: return
        if (mc.currentPosition > 4000L) {
            mc.seekTo(0L)
        } else {
            mc.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPosition.value = positionMs
        controller?.seekTo(positionMs)
        persistQueue()
    }

    fun toggleShuffle() {
        val mc = controller ?: return
        val next = !mc.shuffleModeEnabled
        mc.shuffleModeEnabled = next
        _isShuffle.value = next
    }

    fun cycleRepeatMode() {
        val mc = controller ?: return
        val next = when (mc.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        mc.repeatMode = next
        _repeatMode.value = next
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        controller?.setPlaybackSpeed(clamped)
    }

    fun playNextInQueue(song: Song) {
        val mc = controller ?: return
        val currentQ = _queue.value
        if (currentQ.isEmpty()) {
            playSongs(listOf(song), 0)
            return
        }
        val insertIndex = (mc.currentMediaItemIndex + 1).coerceAtMost(currentQ.size)
        val updated = currentQ.toMutableList().apply { add(insertIndex, song) }
        _queue.value = updated
        mc.addMediaItem(insertIndex, song.toMediaItem())
        persistQueue()
    }

    fun removeFromQueue(index: Int) {
        val mc = controller ?: return
        val currentQ = _queue.value
        if (index !in currentQ.indices || currentQ.size <= 1) return
        val updated = currentQ.toMutableList().apply { removeAt(index) }
        _queue.value = updated
        mc.removeMediaItem(index)
        persistQueue()
    }

    fun saveQueueAsPlaylist(name: String) {
        val qIds = _queue.value.map { it.id }
        if (qIds.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, qIds)
        }
    }

    fun toggleFavorite(songId: Long) {
        val isFav = favoriteIds.value.contains(songId)
        viewModelScope.launch {
            repository.toggleFavorite(songId, isFav)
        }
    }

    fun createPlaylist(name: String, songIds: List<Long> = emptyList()) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, songIds)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addSongsToPlaylist(playlist: PlaylistModel, songIdsToAdd: List<Long>) {
        viewModelScope.launch {
            repository.addSongsToPlaylist(playlist.id, playlist.songIds, songIdsToAdd)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun moveSongInPlaylist(playlist: PlaylistModel, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in playlist.songIds.indices || toIndex !in playlist.songIds.indices) return
        val mutable = playlist.songIds.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        viewModelScope.launch {
            repository.reorderPlaylistSongs(playlist.id, mutable)
        }
    }

    fun toggleSongSelection(songId: Long) {
        val set = _selectedSongIds.value.toMutableSet()
        if (!set.add(songId)) set.remove(songId)
        _selectedSongIds.value = set
    }

    fun clearSongSelection() {
        _selectedSongIds.value = emptySet()
    }

    fun excludeFolder(path: String) {
        viewModelScope.launch {
            repository.excludeFolder(path)
        }
    }

    fun includeFolder(path: String) {
        viewModelScope.launch {
            repository.includeFolder(path)
        }
    }

    fun saveSongMetadataOverride(
        song: Song,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        customLyrics: String
    ) {
        viewModelScope.launch {
            repository.saveMetadataOverride(
                MetadataOverrideEntity(
                    songId = song.id,
                    title = title.trim().ifEmpty { song.title },
                    artist = artist.trim().ifEmpty { song.artist },
                    album = album.trim().ifEmpty { song.album },
                    genre = genre.trim(),
                    year = year,
                    customLyrics = customLyrics
                )
            )
            rescanLibrary()
        }
    }

    fun saveCustomEqPreset(name: String, bands: List<Int>, bass: Int, virt: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveCustomEqPreset(name, bands, bass, virt)
        }
    }

    fun deleteCustomEqPreset(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomEqPreset(id)
        }
    }

    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
        repository.saveSortOption(option)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        repository.saveSettings(updated)
    }

    fun resetSettingsToDefaults() {
        val defaults = AppSettings()
        _settings.value = defaults
        repository.saveSettings(defaults)
    }

    fun shareSong(song: Song) {
        try {
            val ctx = getApplication<Application>()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = song.mimeType.ifEmpty { "audio/*" }
                putExtra(Intent.EXTRA_STREAM, song.uri)
                putExtra(Intent.EXTRA_TEXT, "${song.title} – ${song.artist}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share ${song.title}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    fun deleteSongFromDevice(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.delete(song.uri, null, null)
            } catch (_: Exception) {
            }
            rescanLibrary()
        }
    }

    fun exportPlaylistsJson(): String {
        return repository.exportPlaylistsToJson(playlists.value)
    }

    private fun Song.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(albumArtUri)
                    .build()
            )
            .build()
    }

    override fun onCleared() {
        persistQueue()
        mediaObserver?.let { scanner.unregisterMediaObserver(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
