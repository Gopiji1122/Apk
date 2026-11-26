package com.offlinepdf.studio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecentDocuments(limit: Int = 10): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY lastOpenedAt DESC")
    fun observeFavorites(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isScanned = 1 ORDER BY lastOpenedAt DESC")
    fun observeScannedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE uriString = :uri LIMIT 1")
    suspend fun getByUri(uri: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("UPDATE documents SET lastReadPage = :page, lastOpenedAt = :timestamp WHERE uriString = :uri")
    suspend fun updateReadingProgress(uri: String, page: Int, timestamp: Long)

    @Query("UPDATE documents SET isFavorite = :favorite WHERE uriString = :uri")
    suspend fun setFavorite(uri: String, favorite: Boolean)

    @Query("UPDATE documents SET displayName = :newName WHERE uriString = :uri")
    suspend fun updateDisplayName(uri: String, newName: String)

    @Query("DELETE FROM documents WHERE uriString = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT * FROM bookmarks WHERE documentUri = :uri ORDER BY pageIndex ASC")
    fun observeBookmarks(uri: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE documentUri = :uri AND pageIndex = :pageIndex")
    suspend fun deleteBookmark(uri: String, pageIndex: Int)
}
