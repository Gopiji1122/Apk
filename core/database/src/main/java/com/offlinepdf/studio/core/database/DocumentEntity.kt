package com.offlinepdf.studio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastOpenedAt: Long,
    val lastReadPage: Int = 0,
    val isFavorite: Boolean = false,
    val isScanned: Boolean = false,
    val folderName: String = "Imported"
)

@Entity(tableName = "bookmarks", primaryKeys = ["documentUri", "pageIndex"])
data class BookmarkEntity(
    val documentUri: String,
    val pageIndex: Int,
    val label: String,
    val createdAt: Long
)
