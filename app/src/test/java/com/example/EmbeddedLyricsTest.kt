package com.example

import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.LyricsKind
import com.example.data.lyrics.LyricsSource
import com.example.data.lyrics.DEFAULT_LYRICS_SOURCE_ORDER
import com.example.data.lyrics.LyricsChoice
import com.example.data.lyrics.autoSource
import com.example.data.lyrics.encodeChoice
import com.example.data.lyrics.parseChoice
import com.example.data.lyrics.parseSourceOrder
import com.example.data.lyrics.readId3Lyrics
import com.example.data.lyrics.shouldAskLrclib
import com.example.data.lyrics.sortEmbedded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class EmbeddedLyricsTest {

    private fun utf16(s: String) = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + s.toByteArray(Charsets.UTF_16LE)

    /** USLT as Genius-tagged files have it: UTF-16, language XXX, an empty BOM-only description. */
    private fun uslt(text: String, lang: String = "XXX"): ByteArray =
        byteArrayOf(1) + lang.toByteArray() + utf16("") + byteArrayOf(0, 0) + utf16(text)

    private fun frameV3(id: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray())
        out.write(byteArrayOf((body.size ushr 24).toByte(), (body.size ushr 16).toByte(), (body.size ushr 8).toByte(), body.size.toByte()))
        out.write(byteArrayOf(0, 0))
        out.write(body)
        return out.toByteArray()
    }

    private fun synchsafe(n: Int) = byteArrayOf((n ushr 21 and 0x7F).toByte(), (n ushr 14 and 0x7F).toByte(), (n ushr 7 and 0x7F).toByte(), (n and 0x7F).toByte())

    private fun tag(version: Int, frames: ByteArray, padding: Int = 64): ByteArray {
        val body = frames + ByteArray(padding)
        return "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) + synchsafe(body.size) + body + byteArrayOf(0xFF.toByte(), 0xFB.toByte())
    }

    @Test
    fun `plain lyrics tags read like the ones in real files, in order`() {
        val japanese = "[YOASOBI「群青」歌詞]\r\n\r\n[Verse 1]\r\n嗚呼いつもの様に\r\n過ぎる日々にあくびが出る"
        val romaji = "[YOASOBI「群青」羅馬拼音歌詞]\n\n[Verse 1]\nAa itsumo no you ni\nSugiru hibi ni akubi ga deru"
        val bytes = tag(3, frameV3("APIC", ByteArray(300) { 7 }) + frameV3("USLT", uslt(japanese)) + frameV3("USLT", uslt(romaji, "eng")))

        val found = readId3Lyrics(bytes.inputStream())

        assertEquals(2, found.size)
        assertEquals("", found[0].description)
        val first = found[0].lyrics as Lyrics.Plain
        assertTrue(first.text.startsWith("[YOASOBI「群青」歌詞]\n\n[Verse 1]\n嗚呼"))
        assertTrue((found[1].lyrics as Lyrics.Plain).text.endsWith("akubi ga deru"))
    }

    @Test
    fun `synced tags in v2_4 utf-8 become timed lines, karaoke syllables joined`() {
        fun entry(text: String, ms: Int) = text.toByteArray() + byteArrayOf(0) + byteArrayOf((ms ushr 24).toByte(), (ms ushr 16).toByte(), (ms ushr 8).toByte(), ms.toByte())
        val body = byteArrayOf(3) + "jpn".toByteArray() + byteArrayOf(2, 1) + "romaji".toByteArray() + byteArrayOf(0) +
            entry("Yoru ", 1000) + entry("no machi", 1500) + entry("\nKimi no", 4000) + entry(" koe", 4600)
        val frame = "SYLT".toByteArray() + synchsafe(body.size) + byteArrayOf(0, 0) + body

        val found = readId3Lyrics(tag(4, frame).inputStream()).single()

        assertEquals("romaji", found.description)
        assertEquals(listOf(LyricLine(1000, "Yoru no machi"), LyricLine(4000, "Kimi no koe")), (found.lyrics as Lyrics.Synced).lines)
    }

    @Test
    fun `a plain tag holding LRC text counts as synced`() {
        val found = readId3Lyrics(tag(3, frameV3("USLT", uslt("[00:01.00]one\n[00:02.50]two"))).inputStream()).single()
        assertEquals(listOf(LyricLine(1000, "one"), LyricLine(2500, "two")), (found.lyrics as Lyrics.Synced).lines)
    }

    @Test
    fun `files without a tag or lyrics give nothing`() {
        assertEquals(emptyList<Any>(), readId3Lyrics(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 1, 2).inputStream()))
        assertEquals(emptyList<Any>(), readId3Lyrics(tag(3, frameV3("TIT2", byteArrayOf(0) + "Title".toByteArray())).inputStream()))
        assertEquals(emptyList<Any>(), readId3Lyrics(ByteArray(0).inputStream()))
    }

    @Test
    fun `file versions are sorted by heading and script, headings dropped`() {
        val tags = readId3Lyrics(
            tag(
                3,
                frameV3("USLT", uslt("[MOB CHOIR「1」歌詞]\n\nMob, Mob wow\n伸ばした手は届かない")) +
                    // Mostly English words, so only the heading says it's romaji.
                    frameV3("USLT", uslt("[MB CHOIR「1」羅馬拼音歌詞]\n\n[Intro]\nMob, Mob wow\nNobashita te wa todokanai"))
            ).inputStream()
        )

        val sorted = sortEmbedded(tags, title = "1")

        assertEquals(listOf(LyricsKind.ORIGINAL, LyricsKind.ROMAJI), sorted.map { it.first })
        assertEquals("Mob, Mob wow\n伸ばした手は届かない", (sorted[0].second as Lyrics.Plain).text)
        assertTrue((sorted[1].second as Lyrics.Plain).text.startsWith("[Intro]"))
    }

    @Test
    fun `an English song's lyrics are its original, whatever the language code`() {
        val tags = readId3Lyrics(tag(3, frameV3("USLT", uslt("[Verse 1]\nWise men say\nOnly fools rush in"))).inputStream())
        assertEquals(listOf(LyricsKind.ORIGINAL), sortEmbedded(tags, "Can't Help Falling in Love").map { it.first })
    }

    private val synced = Lyrics.Synced(listOf(LyricLine(0, "words")))
    private val plain = Lyrics.Plain("words")

    @Test
    fun `the automatic pick is synced first when preferred, then the user's order`() {
        val order = DEFAULT_LYRICS_SOURCE_ORDER // .lrc, LRCLIB, the song file: 0.1's order
        // A plain .lrc gives way to a synced LRCLIB version...
        val both = mapOf(LyricsSource.LRC_FILE to plain, LyricsSource.LRCLIB to synced, LyricsSource.EMBEDDED to plain)
        assertEquals(LyricsSource.LRCLIB, autoSource(both, order, preferSynced = true))
        // ...unless synced isn't preferred: then the order wins, synced or not.
        assertEquals(LyricsSource.LRC_FILE, autoSource(both, order, preferSynced = false))
        // All plain, or all synced: the order decides.
        assertEquals(LyricsSource.LRCLIB, autoSource(mapOf(LyricsSource.EMBEDDED to plain, LyricsSource.LRCLIB to plain), order, true))
        assertEquals(
            LyricsSource.EMBEDDED,
            autoSource(mapOf(LyricsSource.EMBEDDED to synced, LyricsSource.LRCLIB to synced), listOf(LyricsSource.EMBEDDED, LyricsSource.LRCLIB), true)
        )
        assertNull(autoSource(emptyMap(), order, true))
    }

    @Test
    fun `LRCLIB is asked unless a source ranked above it already has synced lyrics`() {
        val order = DEFAULT_LYRICS_SOURCE_ORDER
        // As in 0.1: no synced .lrc means look it up; synced lyrics in the file don't count, they rank below.
        assertTrue(shouldAskLrclib(setOf(LyricsSource.EMBEDDED), order))
        assertFalse(shouldAskLrclib(setOf(LyricsSource.LRC_FILE), order))
        // With the song file ranked first, its synced lyrics are enough.
        assertFalse(shouldAskLrclib(setOf(LyricsSource.EMBEDDED), listOf(LyricsSource.EMBEDDED, LyricsSource.LRCLIB, LyricsSource.LRC_FILE)))
    }

    @Test
    fun `a song's choices round-trip, and what 0_2 saved still reads`() {
        val c = LyricsChoice(LyricsKind.ROMAJI, mapOf(LyricsKind.ORIGINAL to LyricsSource.LRC_FILE, LyricsKind.ROMAJI to LyricsSource.EMBEDDED))
        assertEquals(c, parseChoice(encodeChoice(c)))
        assertEquals(LyricsChoice(), parseChoice(encodeChoice(LyricsChoice())))
        // 0.2 saved "ROMAJI" (an LRCLIB version) or "EMBEDDED:ROMAJI" (one from the file).
        assertEquals(LyricsChoice(LyricsKind.ROMAJI), parseChoice("ROMAJI"))
        assertEquals(LyricsChoice(LyricsKind.ROMAJI, mapOf(LyricsKind.ROMAJI to LyricsSource.EMBEDDED)), parseChoice("EMBEDDED:ROMAJI"))
        assertEquals(LyricsChoice(), parseChoice("NONSENSE"))
        assertEquals(LyricsChoice(), parseChoice(null))
    }

    @Test
    fun `the source order reads back, filling in anything missing`() {
        assertEquals(DEFAULT_LYRICS_SOURCE_ORDER, parseSourceOrder(null))
        assertEquals(
            listOf(LyricsSource.EMBEDDED, LyricsSource.LRC_FILE, LyricsSource.LRCLIB),
            parseSourceOrder("EMBEDDED,LRC_FILE,LRCLIB")
        )
        // An unknown or missing name is dropped and the rest follow in the default order.
        assertEquals(listOf(LyricsSource.LRCLIB, LyricsSource.LRC_FILE, LyricsSource.EMBEDDED), parseSourceOrder("LRCLIB,BOGUS"))
    }
}
