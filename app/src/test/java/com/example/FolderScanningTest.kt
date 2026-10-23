package com.example

import com.example.data.repository.audioFormatLabel
import com.example.data.repository.folderNameFromTreeUri
import com.example.data.repository.isTrackInFolder
import com.example.data.repository.supportedAudioMime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderScanningTest {

    @Test
    fun `supported audio is detected by mime type or extension`() {
        assertEquals("audio/mpeg", supportedAudioMime("track.mp3", "application/octet-stream"))
        assertEquals("audio/mpeg", supportedAudioMime("TRACK.MP3", ""))
        assertEquals("audio/mpeg", supportedAudioMime("track", "audio/mp3"))
        assertEquals("audio/flac", supportedAudioMime("song.FLAC", "application/octet-stream"))
        assertEquals("audio/flac", supportedAudioMime("song", "audio/x-flac"))
        assertEquals("audio/mp4", supportedAudioMime("song.m4a", "audio/x-m4a"))
        assertEquals("audio/ogg", supportedAudioMime("song.ogg", "application/ogg"))
        assertEquals("audio/opus", supportedAudioMime("song.opus", ""))
        assertEquals("audio/wav", supportedAudioMime("song.wav", "audio/x-wav"))
    }

    @Test
    fun `non-audio files and directories are rejected`() {
        assertNull(supportedAudioMime("notes.txt", "text/plain"))
        assertNull(supportedAudioMime("cover.jpg", "image/jpeg"))
        assertNull(supportedAudioMime("folder", "vnd.android.document/directory"))
        assertNull(supportedAudioMime("playlist.m3u", "audio/x-mpegurl"))
    }

    @Test
    fun `tracks scanned through a parent folder still belong to nested folders`() {
        val base = "content://com.android.externalstorage.documents/tree/"
        val music = base + "primary%3AMusic"
        val jpop = base + "primary%3AMusic%2FJ-pop"
        val random = base + "primary%3AMusic%2FRandom"
        // Scanned through Music, so its URI is under the Music tree
        val song = "$music/document/primary%3AMusic%2FJ-pop%2FSparkle.mp3"

        assertTrue(isTrackInFolder(song, music))
        assertTrue(isTrackInFolder(song, jpop))
        assertFalse(isTrackInFolder(song, random))
        // "J-pop" must not match a sibling like "J-pop Live"
        assertFalse(isTrackInFolder("$music/document/primary%3AMusic%2FJ-pop%20Live%2Fa.mp3", jpop))
        // Whole-storage root contains everything on that volume
        assertTrue(isTrackInFolder(song, base + "primary%3A"))
        // Different provider never matches by path
        assertFalse(isTrackInFolder("content://other.provider/tree/x/document/primary%3AMusic%2FJ-pop%2Fa.mp3", jpop))
    }

    @Test
    fun `format labels are short codec names`() {
        assertEquals("MP3", audioFormatLabel("audio/mpeg"))
        assertEquals("FLAC", audioFormatLabel("audio/flac"))
        assertEquals("M4A", audioFormatLabel("audio/mp4"))
    }

    @Test
    fun `folder names come from the last segment of the tree uri`() {
        val base = "content://com.android.externalstorage.documents/tree/"
        assertEquals("Music", folderNameFromTreeUri(base + "primary%3AMusic"))
        assertEquals("Rock", folderNameFromTreeUri(base + "primary%3AMusic%2FRock"))
        assertEquals("Internal storage", folderNameFromTreeUri(base + "primary%3A"))
    }
}
