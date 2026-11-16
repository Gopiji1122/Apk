package com.aura.audiopro.data.repository

import android.content.Context
import com.aura.audiopro.data.db.*
import com.aura.audiopro.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject

class MusicRepository(context: Context, private val dao: AuraDao) {
    private val prefs = context.getSharedPreferences("aura_audio_pro_prefs", Context.MODE_PRIVATE)

    val favoritesFlow: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val playHistoryFlow: Flow<List<PlayHistoryEntity>> = dao.observePlayHistory()
    val excludedFoldersFlow: Flow<List<ExcludedFolderEntity>> = dao.observeExcludedFolders()
    val metadataOverridesFlow: Flow<List<MetadataOverrideEntity>> = dao.observeMetadataOverrides()
    val customEqPresetsFlow: Flow<List<CustomEqPresetEntity>> = dao.observeCustomEqPresets()

    val playlistsFlow: Flow<List<PlaylistModel>> = combine(
        dao.observePlaylists(),
        dao.observeAllPlaylistSongs()
    ) { playlists, refs ->
        val grouped = refs.groupBy { it.playlistId }
        playlists.map { p ->
            val orderedIds = grouped[p.id].orEmpty().sortedBy { it.position }.map { it.songId }
            PlaylistModel(
                id = p.id,
                name = p.name,
                createdAt = p.createdAt,
                songIds = orderedIds
            )
        }
    }

    suspend fun toggleFavorite(songId: Long, currentlyFav: Boolean) {
        if (currentlyFav) {
            dao.deleteFavorite(songId)
        } else {
            dao.insertFavorite(FavoriteEntity(songId = songId))
        }
    }

    suspend fun recordPlay(songId: Long) {
        val existing = dao.getPlayHistoryForSong(songId)
        val nextCount = (existing?.playCount ?: 0) + 1
        dao.upsertPlayHistory(
            PlayHistoryEntity(
                songId = songId,
                playCount = nextCount,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun createPlaylist(name: String, initialSongIds: List<Long> = emptyList()): Long {
        val pid = dao.insertPlaylist(PlaylistEntity(name = name.trim()))
        initialSongIds.forEachIndexed { idx, sid ->
            dao.insertPlaylistSong(PlaylistSongCrossRef(pid, sid, idx))
        }
        return pid
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        dao.renamePlaylist(playlistId, newName.trim())
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.clearPlaylistSongs(playlistId)
        dao.deletePlaylistById(playlistId)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, currentSongIds: List<Long>, newSongIds: List<Long>) {
        val merged = (currentSongIds + newSongIds).distinct()
        dao.clearPlaylistSongs(playlistId)
        merged.forEachIndexed { index, sid ->
            dao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, sid, index))
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        dao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun reorderPlaylistSongs(playlistId: Long, orderedSongIds: List<Long>) {
        dao.clearPlaylistSongs(playlistId)
        orderedSongIds.forEachIndexed { index, sid ->
            dao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, sid, index))
        }
    }

    suspend fun excludeFolder(path: String) {
        dao.addExcludedFolder(ExcludedFolderEntity(path))
    }

    suspend fun includeFolder(path: String) {
        dao.removeExcludedFolder(path)
    }

    suspend fun saveMetadataOverride(override: MetadataOverrideEntity) {
        dao.upsertMetadataOverride(override)
    }

    suspend fun saveCustomEqPreset(name: String, bands: List<Int>, bass: Int, virtualizer: Int) {
        dao.insertCustomEqPreset(
            CustomEqPresetEntity(
                name = name.trim(),
                bandLevelsCsv = bands.joinToString(","),
                bassStrength = bass,
                virtualizerStrength = virtualizer
            )
        )
    }

    suspend fun deleteCustomEqPreset(id: Long) {
        dao.deleteCustomEqPreset(id)
    }

    fun saveQueueState(queueIds: List<Long>, currentIndex: Int, positionMs: Long) {
        if (queueIds.isEmpty()) return
        prefs.edit()
            .putString("queue_ids", queueIds.joinToString(","))
            .putInt("queue_index", currentIndex)
            .putLong("queue_pos", positionMs)
            .apply()
    }

    fun getSavedQueueIds(): List<Long> {
        val raw = prefs.getString("queue_ids", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun getSavedQueueIndex(): Int = prefs.getInt("queue_index", 0)
    fun getSavedQueuePos(): Long = prefs.getLong("queue_pos", 0L)

    fun loadSettings(): AppSettings {
        val themeName = prefs.getString("theme_mode", ThemeMode.AMOLED.name) ?: ThemeMode.AMOLED.name
        val theme = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.AMOLED)
        return AppSettings(
            brandName = prefs.getString("brand_name", "Aura Audio Pro") ?: "Aura Audio Pro",
            themeMode = theme,
            accentIndex = prefs.getInt("accent_index", 0),
            dynamicArtworkColor = prefs.getBoolean("dynamic_art_color", true),
            reducedMotion = prefs.getBoolean("reduced_motion", false),
            vinylMode = prefs.getBoolean("vinyl_mode", false),
            gaplessPlayback = prefs.getBoolean("gapless", true),
            crossfadeSeconds = prefs.getInt("crossfade_sec", 0),
            fontScale = prefs.getFloat("font_scale", 1.0f),
            minAudioDurationSec = prefs.getInt("min_duration_sec", 5),
            homeSections = HomeSectionVisibility(
                showCurrentlyPlaying = prefs.getBoolean("home_now_playing", true),
                showRecentlyPlayed = prefs.getBoolean("home_recent_played", true),
                showRecentlyAdded = prefs.getBoolean("home_recent_added", true),
                showMostPlayed = prefs.getBoolean("home_most_played", true),
                showFavorites = prefs.getBoolean("home_favorites", true),
                showPlaylists = prefs.getBoolean("home_playlists", true),
                showStats = prefs.getBoolean("home_stats", true)
            )
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString("brand_name", settings.brandName)
            .putString("theme_mode", settings.themeMode.name)
            .putInt("accent_index", settings.accentIndex)
            .putBoolean("dynamic_art_color", settings.dynamicArtworkColor)
            .putBoolean("reduced_motion", settings.reducedMotion)
            .putBoolean("vinyl_mode", settings.vinylMode)
            .putBoolean("gapless", settings.gaplessPlayback)
            .putInt("crossfade_sec", settings.crossfadeSeconds)
            .putFloat("font_scale", settings.fontScale)
            .putInt("min_duration_sec", settings.minAudioDurationSec)
            .putBoolean("home_now_playing", settings.homeSections.showCurrentlyPlaying)
            .putBoolean("home_recent_played", settings.homeSections.showRecentlyPlayed)
            .putBoolean("home_recent_added", settings.homeSections.showRecentlyAdded)
            .putBoolean("home_most_played", settings.homeSections.showMostPlayed)
            .putBoolean("home_favorites", settings.homeSections.showFavorites)
            .putBoolean("home_playlists", settings.homeSections.showPlaylists)
            .putBoolean("home_stats", settings.homeSections.showStats)
            .apply()
    }

    fun loadSortOption(): SortOption {
        val raw = prefs.getString("sort_option", SortOption.TITLE_ASC.name) ?: SortOption.TITLE_ASC.name
        return runCatching { SortOption.valueOf(raw) }.getOrDefault(SortOption.TITLE_ASC)
    }

    fun saveSortOption(option: SortOption) {
        prefs.edit().putString("sort_option", option.name).apply()
    }

    fun exportPlaylistsToJson(playlists: List<PlaylistModel>): String {
        val arr = JSONArray()
        playlists.forEach { p ->
            val obj = JSONObject()
            obj.put("name", p.name)
            val ids = JSONArray()
            p.songIds.forEach { ids.put(it) }
            obj.put("songIds", ids)
            arr.put(obj)
        }
        return arr.toString(2)
    }
}
