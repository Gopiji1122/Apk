package com.aura.audiopro.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String,
    val filePath: String,
    val folderName: String,
    val folderPath: String,
    val uriString: String,
    val albumArtUriString: String,
    val genre: String = "",
    val year: Int = 0
) {
    val uri: Uri get() = Uri.parse(uriString)
    val albumArtUri: Uri get() = Uri.parse(albumArtUriString)
}

data class SongTechDetails(
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val formatLabel: String,
    val channelsLabel: String,
    val embeddedLyrics: String?
)

data class LrcLine(
    val timeMs: Long,
    val text: String
)

data class PlaylistModel(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songIds: List<Long>
)

data class DuplicateGroup(
    val key: String,
    val songs: List<Song>
)

enum class SortOption(val label: String) {
    TITLE_ASC("Title (A–Z)"),
    TITLE_DESC("Title (Z–A)"),
    ARTIST_ASC("Artist (A–Z)"),
    ALBUM_ASC("Album (A–Z)"),
    DATE_ADDED_DESC("Recently Added"),
    DURATION_DESC("Duration (Longest)"),
    PLAY_COUNT_DESC("Most Played")
}

enum class ThemeMode(val label: String) {
    AMOLED("AMOLED Black"),
    DARK("Slate Dark"),
    LIGHT("Clean Light")
}

enum class MainDestination(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    PLAYLISTS("Playlists"),
    EQUALIZER("Equalizer"),
    FOLDERS("Folders"),
    SETTINGS("Settings")
}

data class HomeSectionVisibility(
    val showCurrentlyPlaying: Boolean = true,
    val showRecentlyPlayed: Boolean = true,
    val showRecentlyAdded: Boolean = true,
    val showMostPlayed: Boolean = true,
    val showFavorites: Boolean = true,
    val showPlaylists: Boolean = true,
    val showStats: Boolean = true
)

data class AppSettings(
    val brandName: String = "Aura Audio Pro",
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val accentIndex: Int = 0,
    val dynamicArtworkColor: Boolean = true,
    val reducedMotion: Boolean = false,
    val vinylMode: Boolean = false,
    val gaplessPlayback: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val fontScale: Float = 1.0f,
    val minAudioDurationSec: Int = 5,
    val homeSections: HomeSectionVisibility = HomeSectionVisibility()
)
