package com.example

import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testTrackDurationFormatting() {
        assertEquals("00:00", Track.formatDuration(0L))
        assertEquals("00:05", Track.formatDuration(5000L))
        assertEquals("01:30", Track.formatDuration(90000L))
        assertEquals("01:05:23", Track.formatDuration(3923000L))
    }

    @Test
    fun testTrackModelDefaults() {
        val track = Track(
            id = "test_1",
            contentUri = "content://media/audio/1",
            title = "Neon Nightfall",
            artist = "Moon Cat",
            album = "Terminal Sessions",
            durationMs = 180000L
        )
        assertEquals("03:00", track.formattedDuration)
        assertEquals(320, track.bitrateKbps)
        assertEquals("audio/mpeg", track.mimeType)
    }
}
