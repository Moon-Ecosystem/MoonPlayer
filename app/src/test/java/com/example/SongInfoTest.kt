package com.example

import com.example.data.InfoSection
import com.example.data.codecLabel
import com.example.data.songInfoText
import org.junit.Assert.assertEquals
import org.junit.Test

class SongInfoTest {

    @Test
    fun `codec names come from the stream, not the file's extension`() {
        assertEquals("AAC-LC", codecLabel("audio/mp4a-latm", "mp4a.40.2"))
        assertEquals("HE-AAC", codecLabel("audio/mp4a-latm", "mp4a.40.5"))
        assertEquals("AAC", codecLabel("audio/mp4a-latm", null))
        assertEquals("MPEG Layer III", codecLabel("audio/mpeg", null))
        assertEquals("FLAC", codecLabel("audio/flac", null))
        assertEquals("Unknown", codecLabel(null, null))
    }

    @Test
    fun `copied text lists every section and row`() {
        val text = songInfoText(
            "Song",
            listOf(InfoSection("FILE", listOf("Name" to "a.m4a", "Size" to "3.4 MB")), InfoSection("TAGS", listOf("Title" to "Song")))
        )
        assertEquals("Song\n\nFILE\nName: a.m4a\nSize: 3.4 MB\n\nTAGS\nTitle: Song", text)
    }
}
