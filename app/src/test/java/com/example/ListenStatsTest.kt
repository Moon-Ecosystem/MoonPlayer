package com.example

import com.example.data.model.Track
import com.example.viewmodel.LibrarySortOrder
import com.example.viewmodel.ListenTimer
import com.example.viewmodel.searchAndSort
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenStatsTest {

    private var clock = 0L
    private val timer = ListenTimer { clock }

    @Test
    fun `only playing time counts, not pauses`() {
        timer.setPlaying(true)
        clock = 10_000
        timer.setPlaying(false)
        clock = 60_000 // paused for 50 s
        timer.setPlaying(true)
        clock = 65_000
        assertEquals(15_000, timer.take())
    }

    @Test
    fun `a song skipped after 3 seconds counts 3 seconds`() {
        timer.setPlaying(true)
        clock = 3_000
        assertEquals(3_000, timer.take())
        // The next song keeps playing and starts from zero.
        clock = 5_000
        assertEquals(2_000, timer.take())
    }

    @Test
    fun `repeated play events and takes don't double count`() {
        timer.setPlaying(true)
        clock = 1_000
        timer.setPlaying(true)
        clock = 2_000
        timer.setPlaying(false)
        timer.setPlaying(false)
        assertEquals(2_000, timer.take())
        assertEquals(0, timer.take())
    }

    private fun track(title: String, artist: String, ms: Long) =
        Track(id = title, contentUri = title, title = title, artist = artist, album = "", durationMs = ms)

    @Test
    fun `search and sort work on any song list`() {
        val songs = listOf(track("b", "Zed", 3), track("a", "Amy", 1), track("c", "Amy", 2))
        assertEquals(listOf("a", "b", "c"), searchAndSort(songs, "", LibrarySortOrder.TITLE_ASC).map { it.title })
        assertEquals(listOf("b", "c", "a"), searchAndSort(songs, "", LibrarySortOrder.DURATION_DESC).map { it.title })
        assertEquals(listOf("a", "c"), searchAndSort(songs, "amy", LibrarySortOrder.TITLE_ASC).map { it.title })
    }

    @Test
    fun `album sort keeps each album in track order`() {
        val songs = listOf(
            track("z", "A", 1).copy(album = "Milk", trackNumber = 2),
            track("y", "A", 1).copy(album = "Alpha", trackNumber = 1),
            track("x", "A", 1).copy(album = "Milk", trackNumber = 1)
        )
        assertEquals(listOf("y", "x", "z"), searchAndSort(songs, "", LibrarySortOrder.ALBUM_ASC).map { it.title })
    }
}
