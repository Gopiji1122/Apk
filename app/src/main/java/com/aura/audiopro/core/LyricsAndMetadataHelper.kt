package com.aura.audiopro.core

import android.content.Context
import android.media.MediaMetadataRetriever
import com.aura.audiopro.model.DuplicateGroup
import com.aura.audiopro.model.LrcLine
import com.aura.audiopro.model.Song
import com.aura.audiopro.model.SongTechDetails
import com.aura.audiopro.model.SortOption
import java.io.File

object LyricsAndMetadataHelper {

    private val lrcRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")

    fun parseLrcText(rawLrc: String): List<LrcLine> {
        if (rawLrc.isBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        rawLrc.lineSequence().forEach { line ->
            val match = lrcRegex.find(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = match.groupValues[3]
                val ms = when (fracRaw.length) {
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    3 -> fracRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                val totalMs = min * 60_000L + sec * 1_000L + ms
                if (text.isNotEmpty()) {
                    lines.add(LrcLine(timeMs = totalMs, text = text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun loadLocalLrcForSong(song: Song): List<LrcLine> {
        try {
            if (song.filePath.isNotBlank()) {
                val audioFile = File(song.filePath)
                val baseName = audioFile.nameWithoutExtension
                val lrcFile = File(audioFile.parentFile, "$baseName.lrc")
                if (lrcFile.exists() && lrcFile.canRead()) {
                    return parseLrcText(lrcFile.readText())
                }
            }
        } catch (_: Exception) {
        }
        return emptyList()
    }

    fun inspectSongTechDetails(context: Context, song: Song): SongTechDetails {
        val retriever = MediaMetadataRetriever()
        var bitrateKbps = 0
        var sampleRateHz = 44100
        var channelsLabel = "Stereo"
        try {
            retriever.setDataSource(context, song.uri)
            val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            bitrateKbps = (br / 1000).coerceAtLeast(0)
            if ( android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val sr = retriever.extractMetadata(38 /* METADATA_KEY_SAMPLERATE */)?.toIntOrNull()
                if (sr != null && sr > 0) sampleRateHz = sr
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val ext = song.filePath.substringAfterLast('.', "").uppercase().ifEmpty {
            song.mimeType.substringAfterLast('/').uppercase()
        }
        if (bitrateKbps == 0 && song.duration > 0L && song.size > 0L) {
            bitrateKbps = ((song.size * 8) / song.duration).toInt()
        }

        return SongTechDetails(
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            formatLabel = ext.ifEmpty { "AUDIO" },
            channelsLabel = channelsLabel,
            embeddedLyrics = null
        )
    }

    fun findDuplicateGroups(songs: List<Song>): List<DuplicateGroup> {
        return songs
            .groupBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}|${it.duration / 1000}" }
            .filter { it.value.size > 1 }
            .map { (key, list) -> DuplicateGroup(key = key, songs = list) }
    }

    fun sortSongs(
        songs: List<Song>,
        option: SortOption,
        playCounts: Map<Long, Int>
    ): List<Song> {
        return when (option) {
            SortOption.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> songs.sortedByDescending { it.title.lowercase() }
            SortOption.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM_ASC -> songs.sortedBy { it.album.lowercase() }
            SortOption.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
            SortOption.DURATION_DESC -> songs.sortedByDescending { it.duration }
            SortOption.PLAY_COUNT_DESC -> songs.sortedByDescending { playCounts[it.id] ?: 0 }
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1.0) "%.2f MB".format(mb) else "${bytes / 1024} KB"
    }
}
