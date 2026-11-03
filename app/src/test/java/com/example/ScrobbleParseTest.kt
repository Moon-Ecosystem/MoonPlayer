package com.example

import com.example.data.scrobble.parseScrobbleStats
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrobbleParseTest {

    @Test
    fun `last fm answers become stats, single items and now playing included`() {
        val json = JSONObject(
            """
            {
              "info": {"user": {"playcount": "52310", "registered": {"unixtime": "1394000000", "#text": 1394000000}}},
              "artists": {"topartists": {"artist": [
                  {"name": "RADWIMPS", "playcount": "96", "url": "https://www.last.fm/music/RADWIMPS"},
                  {"name": "LiSA", "playcount": "34", "url": "https://www.last.fm/music/LiSA"}
                ], "@attr": {"total": "38"}}},
              "albums": {"topalbums": {"album":
                  {"name": "Your Name.", "playcount": "40", "url": "u", "artist": {"name": "RADWIMPS"}},
                "@attr": {"total": "1"}}},
              "tracks": {"toptracks": {"track": [], "@attr": {"total": "0"}}},
              "recent": {"recenttracks": {"track": [
                  {"name": "Sparkle", "artist": {"#text": "RADWIMPS"}, "@attr": {"nowplaying": "true"},
                   "image": [{"#text": "small.jpg"}, {"#text": "big.jpg"}], "url": "a"},
                  {"name": "Rubia", "artist": {"#text": "MOB CHOIR"}, "date": {"uts": "1793700000"},
                   "image": [{"#text": ""}], "url": "b"}
                ]}},
              "count": {"recenttracks": {"track": [], "@attr": {"total": "412"}}}
            }
            """
        )

        val s = parseScrobbleStats(json, fetchedAt = 5L)

        assertEquals(412, s.scrobbles)
        assertEquals(38, s.artists)
        assertEquals(52310, s.totalScrobbles)
        assertEquals(1_394_000_000_000L, s.since)
        assertEquals(listOf("RADWIMPS", "LiSA"), s.topArtists.map { it.name })
        assertEquals(96, s.topArtists[0].plays)
        // One album comes back as an object, not a one-item array.
        assertEquals("Your Name.", s.topAlbums.single().name)
        assertEquals("RADWIMPS", s.topAlbums.single().detail)
        assertEquals(0, s.topTracks.size)
        // Now playing has no time; the largest non-blank cover wins.
        assertNull(s.recent[0].at)
        assertEquals("big.jpg", s.recent[0].imageUrl)
        assertEquals(1_793_700_000_000L, s.recent[1].at)
        assertNull(s.recent[1].imageUrl)
        assertEquals(5L, s.fetchedAt)
    }

    @Test
    fun `without a range count the whole history is the range`() {
        val json = JSONObject("""{"info": {"user": {"playcount": "120"}}}""")
        val s = parseScrobbleStats(json, fetchedAt = 0L)
        assertEquals(120, s.scrobbles)
        assertNull(s.since)
    }
}
