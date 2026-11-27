package com.offlinepdf.studio.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "scan_session_pages")
data class ScanSessionPageEntity(
    @PrimaryKey val id: String,
    val orderIndex: Int,
    val rawImagePath: String,
    val warpedImagePath: String,
    val enhancedImagePath: String,
    val tlX: Float, val tlY: Float,
    val trX: Float, val trY: Float,
    val brX: Float, val brY: Float,
    val blX: Float, val blY: Float,
    val rotationDegrees: Int,
    val fineRotationDegrees: Float,
    val filterModeName: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val sharpness: Float
)

@Dao
interface ScanSessionDao {
    @Query("SELECT * FROM scan_session_pages ORDER BY orderIndex ASC")
    suspend fun getAllOrdered(): List<ScanSessionPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<ScanSessionPageEntity>)

    @Query("DELETE FROM scan_session_pages")
    suspend fun clearAll()
}
