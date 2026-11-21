package com.aura.audiopro.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AuraDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun deleteFavorite(songId: Long)

    @Query("SELECT * FROM play_history")
    fun observePlayHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE songId = :songId LIMIT 1")
    suspend fun getPlayHistoryForSong(songId: Long): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayHistory(entity: PlayHistoryEntity)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("SELECT * FROM playlist_songs ORDER BY position ASC")
    fun observeAllPlaylistSongs(): Flow<List<PlaylistSongCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT * FROM excluded_folders")
    fun observeExcludedFolders(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT * FROM excluded_folders")
    suspend fun getExcludedFoldersOnce(): List<ExcludedFolderEntity>

    @Query("SELECT * FROM metadata_overrides")
    suspend fun getMetadataOverridesOnce(): List<MetadataOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addExcludedFolder(entity: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE folderPath = :path")
    suspend fun removeExcludedFolder(path: String)

    @Query("SELECT * FROM metadata_overrides")
    fun observeMetadataOverrides(): Flow<List<MetadataOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadataOverride(entity: MetadataOverrideEntity)

    @Query("SELECT * FROM eq_presets ORDER BY id ASC")
    fun observeCustomEqPresets(): Flow<List<CustomEqPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomEqPreset(entity: CustomEqPresetEntity)

    @Query("DELETE FROM eq_presets WHERE id = :id")
    suspend fun deleteCustomEqPreset(id: Long)
}
