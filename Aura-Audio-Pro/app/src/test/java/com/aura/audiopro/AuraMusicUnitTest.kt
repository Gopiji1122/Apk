package com.aura.audiopro

import android.net.Uri
import com.aura.audiopro.core.LyricsAndMetadataHelper
import com.aura.audiopro.model.Song
import com.aura.audiopro.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraMusicUnitTest {

    @Test
    fun parseLrcText_parsesTimestampsAndSortsChronologically() {
        val raw = """
            [00:15.50]Second lyric line
            [00:04.20]First lyric line
            [01:02.000]Third lyric line
        """.trimIndent()

        val parsed = LyricsAndMetadataHelper.parseLrcText(raw)
        assertEquals(3, parsed.size)
        assertEquals(4200L, parsed[0].timeMs)
        assertEquals("First lyric line", parsed[0].text)
        assertEquals(15500L, parsed[1].timeMs)
        assertEquals(62000L, parsed[2].timeMs)
    }

    @Test
    fun formatDuration_formatsMinutesSecondsAndHoursAccurately() {
        assertEquals("00:05", LyricsAndMetadataHelper.formatDuration(5000L))
        assertEquals("03:45", LyricsAndMetadataHelper.formatDuration(225_000L))
        assertEquals("1:01:05", LyricsAndMetadataHelper.formatDuration(3_665_000L))
    }

    @Test
    fun findDuplicateGroups_detectsDuplicateTitleArtistAndDuration() {
        val s1 = sampleSong(1L, "Kesariya", "Arijit Singh", 268_000L)
        val s2 = sampleSong(2L, "kesariya ", "arijit singh", 268_400L)
        val s3 = sampleSong(3L, "Tum Hi Ho", "Arijit Singh", 262_000L)

        val groups = LyricsAndMetadataHelper.findDuplicateGroups(listOf(s1, s2, s3))
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().songs.size)
    }

    @Test
    fun sortSongs_sortsByPlayCountAndTitle() {
        val a = sampleSong(1L, "Beta", "Artist A", 200_000L)
        val b = sampleSong(2L, "Alpha", "Artist B", 180_000L)
        val playCounts = mapOf(1L to 10, 2L to 2)

        val byTitle = LyricsAndMetadataHelper.sortSongs(listOf(a, b), SortOption.TITLE_ASC, playCounts)
        assertEquals("Alpha", byTitle.first().title)

        val byMostPlayed = LyricsAndMetadataHelper.sortSongs(listOf(a, b), SortOption.PLAY_COUNT_DESC, playCounts)
        assertEquals("Beta", byMostPlayed.first().title)
    }

    private fun sampleSong(id: Long, title: String, artist: String, duration: Long): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = "Album",
            albumId = 10L,
            duration = duration,
            dateAdded = 1000L,
            size = 4_000_000L,
            mimeType = "audio/mpeg",
            filePath = "/storage/emulated/0/Music/$title.mp3",
            folderName = "Music",
            folderPath = "/storage/emulated/0/Music",
            uriString = "content://media/external/audio/media/$id",
            albumArtUriString = "content://media/external/audio/albumart/10"
        )
    }
}
