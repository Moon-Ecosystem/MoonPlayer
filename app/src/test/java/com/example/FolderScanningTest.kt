package com.example

import com.example.data.repository.SUPPORTED_FORMAT_LABELS
import com.example.data.repository.audioFormatLabel
import com.example.data.model.Track
import com.example.data.repository.containingFolder
import com.example.data.repository.folderPlaylistsOf
import com.example.data.repository.folderNameFromTreeUri
import com.example.data.repository.formatBreakdown
import com.example.data.repository.isTrackInFolder
import com.example.data.repository.pickCoverFile
import com.example.data.repository.pickQuickPlaylists
import com.example.data.repository.supportedAudioMime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderScanningTest {

    @Test
    fun `folder playlists are the real folders, named by path only when names clash`() {
        fun song(id: String, folder: String) = Track(id = id, contentUri = id, title = id, artist = "", album = "", durationMs = 0, folderPath = folder)
        val lists = folderPlaylistsOf(
            listOf(
                song("a", "Music/Random"), song("b", "Music/J-pop"), song("c", "Music/J-pop"),
                song("d", "Music/Live"), song("e", "Videos/Live")
            )
        )
        assertEquals(listOf("J-pop", "Music/Live", "Random", "Videos/Live"), lists.map { it.first })
        assertEquals(listOf("b", "c"), lists.first().second.map { it.id })
    }

    @Test
    fun `a song's folder is where the file sits, not the library folder`() {
        assertEquals("Music/J-pop", containingFolder("primary:Music/J-pop/song.mp3", "Music"))
        assertEquals("Music/J-pop/Live", containingFolder("primary:Music/J-pop/Live/song.mp3", "Music"))
        assertEquals("Music", containingFolder("primary:Music/song.mp3", "Music"))
        // At the top of the storage, or ids that aren't paths: the library folder's name.
        assertEquals("Music", containingFolder("primary:song.mp3", "Music"))
        assertEquals("Music", containingFolder("msf:1234", "Music"))
    }

    @Test
    fun `supported audio is detected by mime type or extension`() {
        assertEquals("audio/mpeg", supportedAudioMime("track.mp3", "application/octet-stream"))
        assertEquals("audio/mpeg", supportedAudioMime("TRACK.MP3", ""))
        assertEquals("audio/mpeg", supportedAudioMime("track", "audio/mp3"))
        assertEquals("audio/flac", supportedAudioMime("song.FLAC", "application/octet-stream"))
        assertEquals("audio/flac", supportedAudioMime("song", "audio/x-flac"))
        assertEquals("audio/mp4", supportedAudioMime("song.m4a", "audio/x-m4a"))
        // What Android's file provider reports for .m4a.
        assertEquals("audio/mp4", supportedAudioMime("song.m4a", "audio/mpeg"))
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
    fun `library format breakdown counts known formats, most common first`() {
        fun t(mime: String) = com.example.data.model.Track("id", "uri", "t", "a", "al", 0L, mimeType = mime)
        val tracks = listOf(t("audio/flac"), t("audio/mpeg"), t("audio/mpeg"), t(""))
        assertEquals(listOf("MP3" to 2, "FLAC" to 1), formatBreakdown(tracks))
        assertTrue(SUPPORTED_FORMAT_LABELS.startsWith("MP3 · FLAC"))
    }

    @Test
    fun `quick playlists put the playing one first, then recent, then the rest`() {
        val all = listOf("Chill", "Gym", "J-pop", "Road trip", "Study")
        assertEquals(listOf("Gym", "Study", "Chill", "J-pop"), pickQuickPlaylists(all, listOf("Study", "Gym", "Old deleted"), playing = "Gym"))
        assertEquals(listOf("Chill", "Gym"), pickQuickPlaylists(all, emptyList(), playing = null, limit = 2))
        assertEquals(emptyList<String>(), pickQuickPlaylists(emptyList(), listOf("Gym"), playing = "Gym"))
    }

    @Test
    fun `folder names come from the last segment of the tree uri`() {
        val base = "content://com.android.externalstorage.documents/tree/"
        assertEquals("Music", folderNameFromTreeUri(base + "primary%3AMusic"))
        assertEquals("Rock", folderNameFromTreeUri(base + "primary%3AMusic%2FRock"))
        assertEquals("Internal storage", folderNameFromTreeUri(base + "primary%3A"))
    }

    @Test
    fun `folder cover image is picked by name, cover first`() {
        assertEquals("Cover.JPG", pickCoverFile(listOf("scan.png", "folder.jpg", "Cover.JPG")))
        assertEquals("folder.webp", pickCoverFile(listOf("back.jpg", "folder.webp")))
        assertEquals("front.jpeg", pickCoverFile(listOf("front.jpeg")))
        assertNull(pickCoverFile(listOf("booklet.jpg", "cover.txt", "cover")))
        assertNull(pickCoverFile(emptyList()))
    }
}
