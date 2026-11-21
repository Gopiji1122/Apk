package com.aura.audiopro.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int,
    val lastPlayedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderPath: String
)

@Entity(tableName = "metadata_overrides")
data class MetadataOverrideEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: Int,
    val customLyrics: String = ""
)

@Entity(tableName = "eq_presets")
data class CustomEqPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val bandLevelsCsv: String,
    val bassStrength: Int,
    val virtualizerStrength: Int
)
