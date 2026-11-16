package com.promusic.player

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val uri: Uri,
    val path: String,
    val dateAdded: Long
) {
    val folderPath: String get() = path.substringBeforeLast('/', "")
    val folderName: String get() = folderPath.substringAfterLast('/', "Unknown")
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

data class Album(val id: Long, val name: String, val artist: String, val songs: List<Song>)
data class Artist(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.map { it.albumId }.distinct().size
}
data class FolderItem(val path: String, val name: String, val songs: List<Song>)

enum class DetailType { ALBUM, ARTIST, FOLDER, PLAYLIST, LIKED, RECENT, MOST_PLAYED, ADDED }
data class Detail(val type: DetailType, val key: String)

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
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

fun fmtTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun fmtTotal(ms: Long): String {
    val m = ms / 60000
    val h = m / 60
    return if (h > 0) "$h hr ${m % 60} min" else "$m min"
}
