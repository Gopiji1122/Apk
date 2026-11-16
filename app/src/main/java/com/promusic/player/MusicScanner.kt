package com.promusic.player

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

fun scanDeviceMusic(context: Context, minDurationMs: Long): List<Song> {
    val result = ArrayList<Song>()
    val collection = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DATE_ADDED
    )
    val selection =
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMs"

    context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

        while (c.moveToNext()) {
            val id = c.getLong(iId)
            val artist = c.getString(iArtist)
            val album = c.getString(iAlbum)
            result.add(
                Song(
                    id = id,
                    title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Unknown artist" else artist,
                    album = if (album.isNullOrBlank() || album == "<unknown>") "Unknown album" else album,
                    albumId = c.getLong(iAlbumId),
                    durationMs = c.getLong(iDur),
                    track = c.getInt(iTrack),
                    uri = ContentUris.withAppendedId(collection, id),
                    path = c.getString(iData) ?: "",
                    dateAdded = c.getLong(iAdded)
                )
            )
        }
    }
    return result.sortedBy { it.title.lowercase() }
}
