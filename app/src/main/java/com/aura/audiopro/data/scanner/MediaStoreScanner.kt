package com.aura.audiopro.data.scanner

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.aura.audiopro.data.db.MetadataOverrideEntity
import com.aura.audiopro.model.Song
import java.io.File

class MediaStoreScanner(private val context: Context) {

    fun scanAudioFiles(
        minDurationMs: Long = 5000L,
        excludedFolders: Set<String> = emptySet(),
        overrides: Map<Long, MetadataOverrideEntity> = emptyMap()
    ): List<Song> {
        val list = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val filePath = cursor.getString(dataCol).orEmpty()
                    val parentFile = if (filePath.isNotEmpty()) File(filePath).parentFile else null
                    val folderPath = parentFile?.absolutePath ?: "/storage/emulated/0/Music"
                    val normFolder = folderPath.trimEnd('/')
                    if (excludedFolders.any { ex -> val normEx = ex.trimEnd('/'); normFolder == normEx || normFolder.startsWith("$normEx/") }) {
                        continue
                    }
                    val folderName = parentFile?.name ?: "Music"

                    val override = overrides[id]
                    val rawTitle = override?.title?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(titleCol)?.trim().orEmpty()
                    val title = if (rawTitle.isNotEmpty()) rawTitle else "Unknown Title"

                    val rawArtist = override?.artist?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(artistCol)?.trim().orEmpty()
                    val artist = if (rawArtist.isNotEmpty() && rawArtist != "<unknown>") rawArtist else "Unknown Artist"

                    val rawAlbum = override?.album?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(albumCol)?.trim().orEmpty()
                    val album = if (rawAlbum.isNotEmpty() && rawAlbum != "<unknown>") rawAlbum else "Unknown Album"

                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durCol).coerceAtLeast(0L)
                    val dateAdded = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val mimeType = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val year = override?.year?.takeIf { it > 0 } ?: cursor.getInt(yearCol)
                    val genre = override?.genre.orEmpty()

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val artUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )

                    list.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            dateAdded = dateAdded,
                            size = size,
                            mimeType = mimeType,
                            filePath = filePath,
                            folderName = folderName,
                            folderPath = folderPath,
                            uriString = contentUri.toString(),
                            albumArtUriString = artUri.toString(),
                            genre = genre,
                            year = year
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun registerMediaObserver(onChanged: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onChanged()
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (_: Exception) {
        }
        return observer
    }

    fun unregisterMediaObserver(observer: ContentObserver) {
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
    }
}
