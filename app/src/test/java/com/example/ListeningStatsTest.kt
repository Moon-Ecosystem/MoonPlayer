package com.example

import com.example.data.db.HistoryEntity
import com.example.data.model.splitArtists
import com.example.data.stats.StatsKind
import com.example.data.stats.StatsRange
import com.example.data.stats.detailStats
import com.example.data.stats.listeningStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ListeningStatsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // Wednesday 2026-10-21 12:00 UTC.
    private val now = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 21, 12, 0) }.timeInMillis
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun play(
        track: String,
        artist: String,
        listened: Long,
        at: Long,
        album: String = "Album $artist",
        duration: Long = 200_000,
        playlist: String? = null
    ) = HistoryEntity(
        trackId = track, title = track, artist = artist, album = album, durationMs = duration,
        uri = track, playedAt = at, listenedMs = listened, playlist = playlist
    )

    @Test
    fun `artists rank by time actually listened, with plays and share`() {
        val rows = listOf(
            play("a1", "Aimer", 180_000, now - 2 * day),
            play("a1", "Aimer", 10_000, now - day),
            play("r1", "RADWIMPS", 60_000, now - day),
            play("r2", "RADWIMPS", 150_000, now - hour)
        )
        val stats = listeningStats(rows, StatsRange.ALL, now, utc)
        assertEquals(400_000, stats.listenedMs)
        assertEquals(4, stats.plays)
        assertEquals(listOf("RADWIMPS", "Aimer"), stats.artists.map { it.name })
        assertEquals(listOf(210_000L, 190_000L), stats.artists.map { it.listenedMs })
        assertEquals(listOf(2, 2), stats.artists.map { it.plays })
    }

    @Test
    fun `finished and skipped plays, ignoring old plays and one that may still be playing`() {
        val rows = listOf(
            play("s", "X", 190_000, now - day), // 95%: finished
            play("s", "X", 5_000, now - day), // 5 s: skipped
            play("s", "X", 60_000, now - day), // neither
            play("s", "X", -1, now - day), // recorded before time was tracked
            play("s", "X", 2_000, now - 10_000) // started 10 s ago, may still be playing
        )
        val track = listeningStats(rows, StatsRange.ALL, now, utc).tracks.single()
        assertEquals(5, track.plays)
        assertEquals(1, track.finished)
        assertEquals(1, track.skipped)
        assertEquals(257_000, track.listenedMs)
    }

    @Test
    fun `a short song stopped halfway is skipped even under 30 s`() {
        val rows = listOf(play("jingle", "X", 9_000, now - day, duration = 20_000))
        assertEquals(1, listeningStats(rows, StatsRange.ALL, now, utc).tracks.single().skipped)
    }

    @Test
    fun `week trend has a bar per day ending today, hours and weekdays add up`() {
        val rows = listOf(
            play("a", "X", 60_000, now - hour), // Wed 11:00
            play("b", "X", 120_000, now - day - hour) // Tue 11:00
        )
        val stats = listeningStats(rows, StatsRange.WEEK, now, utc)
        assertEquals(listOf("T", "F", "S", "S", "M", "T", "W"), stats.trend.map { it.label })
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 120_000L, 60_000L), stats.trend.map { it.ms })
        assertEquals(180_000, stats.byHour[11].ms)
        assertEquals(120_000, stats.byWeekday[1].ms) // Tuesday
        assertEquals(60_000, stats.byWeekday[2].ms) // Wednesday
    }

    @Test
    fun `year trend is 12 months ending this one`() {
        val stats = listeningStats(emptyList(), StatsRange.YEAR, now, utc)
        assertEquals(12, stats.trend.size)
        assertEquals("O", stats.trend.last().label)
        assertEquals(true, stats.trendPerMonth)
    }

    @Test
    fun `range starts at midnight so today is a whole day`() {
        val since = StatsRange.WEEK.since(now, utc)
        val expected = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 15) }.timeInMillis
        assertEquals(expected, since)
        assertEquals(0L, StatsRange.ALL.since(now, utc))
    }

    @Test
    fun `artist tags split on slash and semicolon, but not inside AC-DC or on ampersands`() {
        assertEquals(listOf("RADWIMPS", "Toaka"), splitArtists("RADWIMPS/Toaka"))
        assertEquals(listOf("MOB CHOIR", "sajou no hana"), splitArtists("MOB CHOIR / sajou no hana"))
        assertEquals(listOf("A", "B", "C"), splitArtists("A; B/C"))
        assertEquals(listOf("AC/DC"), splitArtists("AC/DC"))
        assertEquals(listOf("AC/DC", "Foo"), splitArtists("AC/DC/Foo"))
        assertEquals(listOf("Simon & Garfunkel"), splitArtists("Simon & Garfunkel"))
        assertEquals(listOf("Unknown Artist"), splitArtists("Unknown Artist"))
    }

    @Test
    fun `a duet counts fully for each of its artists`() {
        val rows = listOf(
            play("suzume", "RADWIMPS/Toaka", 120_000, now - day),
            play("sparkle", "RADWIMPS", 60_000, now - day)
        )
        val stats = listeningStats(rows, StatsRange.ALL, now, utc)
        assertEquals(listOf("RADWIMPS" to 180_000L, "Toaka" to 120_000L), stats.artists.map { it.name to it.listenedMs })
        assertEquals(180_000, stats.listenedMs) // the song itself is counted once
        val toaka = detailStats(rows, StatsKind.ARTIST, "Toaka", StatsRange.ALL, now, utc)
        assertEquals(listOf("suzume"), toaka.stats.tracks.map { it.name })
    }

    @Test
    fun `albums name their artist and playlists come from where the song was played`() {
        val rows = listOf(
            play("a", "LiSA", 100_000, now - day, album = "Leo-NiNE", playlist = "J-pop"),
            play("b", "LiSA", 50_000, now - day, album = "Leo-NiNE"),
            play("c", "Aimer", 30_000, now - day, album = "Walpurgis", playlist = "J-pop")
        )
        val stats = listeningStats(rows, StatsRange.ALL, now, utc)
        assertEquals("Leo-NiNE", stats.albums.first().name)
        assertEquals("LiSA", stats.albums.first().detail)
        assertEquals(listOf("J-pop"), stats.playlists.map { it.name })
        assertEquals(130_000, stats.playlists.single().listenedMs)
    }
}
