package com.example

import com.example.data.lyrics.LrclibRecord
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.LyricsKind
import com.example.data.lyrics.chooseLyricsKind
import com.example.data.lyrics.currentLineIndex
import com.example.data.lyrics.looksLikeRomaji
import com.example.data.lyrics.parseLrc
import com.example.data.lyrics.pickLyricsVersions
import com.example.data.lyrics.prefersLatinLyrics
import com.example.data.lyrics.siblingLrcDocumentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test
    fun `lrc lines parse with fractions, tags and repeated timestamps`() {
        val lrc = "﻿[ar:YOASOBI]\n[00:00.90] first\n[00:07.7]second\n[01:02.345][00:03]chorus\n[00:14.49] "
        assertEquals(
            listOf(
                LyricLine(900, "first"),
                LyricLine(3_000, "chorus"),
                LyricLine(7_700, "second"),
                LyricLine(14_490, ""),
                LyricLine(62_345, "chorus")
            ),
            parseLrc(lrc)
        )
    }

    @Test
    fun `offset shifts lines and word timings are stripped`() {
        val lrc = "[offset:+500]\n[00:01.00]<00:01.00>Hello <00:01.50>world"
        assertEquals(listOf(LyricLine(500, "Hello world")), parseLrc(lrc))
    }

    @Test
    fun `text without timestamps is not synced`() {
        assertNull(parseLrc("just plain words\nmore words"))
    }

    @Test
    fun `current line is the last one started`() {
        val lines = listOf(LyricLine(1000, "a"), LyricLine(5000, "b"), LyricLine(9000, "c"))
        assertEquals(-1, currentLineIndex(lines, 500))
        assertEquals(0, currentLineIndex(lines, 1000))
        assertEquals(1, currentLineIndex(lines, 8999))
        assertEquals(2, currentLineIndex(lines, 60_000))
    }

    @Test
    fun `romaji is told apart from an english translation`() {
        assertTrue(looksLikeRomaji("[00:01]Shizumu you ni tokete yuku you ni\n[00:07]Futari dake no sora ga hirogaru yoru ni"))
        assertTrue(looksLikeRomaji("[00:01]Kitto zutto matte ita, Tōkyō no yoru"))
        assertFalse(looksLikeRomaji("[00:01]Like sinking, like melting away\n[00:07]In the night when the sky spreads out just for us two"))
    }

    @Test
    fun `versions are sorted by kind, closest length within tolerance`() {
        val records = listOf(
            LrclibRecord("[00:01]沈むように溶けてゆくように", null, 259.0),
            LrclibRecord("[00:01]沈むように 短い版", null, 180.0),
            LrclibRecord("[00:01]Shizumu you ni tokete yuku you ni", null, 260.0),
            LrclibRecord("[00:01]Like sinking, like melting away into the night", null, 258.0),
            LrclibRecord(null, "plain only", 259.0)
        )
        assertEquals(
            mapOf(
                LyricsKind.ORIGINAL to "[00:01]沈むように溶けてゆくように",
                LyricsKind.ROMAJI to "[00:01]Shizumu you ni tokete yuku you ni",
                LyricsKind.TRANSLATION to "[00:01]Like sinking, like melting away into the night"
            ),
            pickLyricsVersions(records, 258.6, title = "夜に駆ける")
        )
    }

    @Test
    fun `a latin-script song has only originals`() {
        val records = listOf(
            LrclibRecord("[00:01]Oh baby baby how was I supposed to know", null, 211.0),
            LrclibRecord("[00:01]Show me how you want it to be", null, 300.0)
        )
        assertEquals(mapOf(LyricsKind.ORIGINAL to "[00:01]Oh baby baby how was I supposed to know"), pickLyricsVersions(records, 211.0, "Baby One More Time"))
        assertEquals(emptyMap<LyricsKind, String>(), pickLyricsVersions(records, 400.0))
    }

    @Test
    fun `the shown version follows the song choice, then the preference`() {
        val all = setOf(LyricsKind.ORIGINAL, LyricsKind.ROMAJI, LyricsKind.TRANSLATION)
        assertEquals(LyricsKind.TRANSLATION, chooseLyricsKind(all, LyricsKind.TRANSLATION, preferRomaji = true))
        assertEquals(LyricsKind.ROMAJI, chooseLyricsKind(all, null, preferRomaji = true))
        assertEquals(LyricsKind.ORIGINAL, chooseLyricsKind(all, null, preferRomaji = false))
        // Romaji preferred but missing: the original, never the translation.
        assertEquals(LyricsKind.ORIGINAL, chooseLyricsKind(setOf(LyricsKind.ORIGINAL, LyricsKind.TRANSLATION), null, preferRomaji = true))
        assertNull(chooseLyricsKind(emptySet(), null, preferRomaji = true))
        assertTrue(prefersLatinLyrics("en"))
        assertFalse(prefersLatinLyrics("ja"))
    }

    @Test
    fun `lrc sibling swaps the extension of path-style ids`() {
        assertEquals("primary:Music/J-pop/a.b.lrc", siblingLrcDocumentId("primary:Music/J-pop/a.b.mp3"))
        assertNull(siblingLrcDocumentId("msf:12345"))
    }
}
