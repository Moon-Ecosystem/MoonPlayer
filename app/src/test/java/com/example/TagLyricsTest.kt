package com.example

import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.LyricsKind
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.lyricsFromMetadata
import com.example.data.lyrics.sortEmbedded
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagLyricsTest {

    @Test
    fun `flac and ogg lyrics comments are read, other comments and the lyricist are not`() {
        val found = lyricsFromMetadata(
            listOf(
                VorbisComment("TITLE", "群青"),
                VorbisComment("LYRICIST", "Ayase"),
                VorbisComment("UNSYNCEDLYRICS", "嗚呼いつもの様に\r\n過ぎる日々にあくびが出る"),
                VorbisComment("LYRICS", "[00:01.00]Aa itsumo no you ni\n[00:04.50]Sugiru hibi ni akubi ga deru"),
                VorbisComment("lyrics-romaji", "Aa itsumo no you ni")
            )
        )

        // Media3 upper-cases comment names; matching ignores case either way.
        assertEquals(listOf("UNSYNCEDLYRICS", "LYRICS", "LYRICS-ROMAJI"), found.map { it.description })
        assertEquals("嗚呼いつもの様に\n過ぎる日々にあくびが出る", (found[0].lyrics as Lyrics.Plain).text)
        assertEquals(LyricLine(4_500, "Sugiru hibi ni akubi ga deru"), (found[1].lyrics as Lyrics.Synced).lines[1])
    }

    @Test
    fun `an m4a's lyrics atom arrives as a USLT frame`() {
        val found = lyricsFromMetadata(
            listOf(
                TextInformationFrame("TIT2", null, listOf("L'AMOUR DE MA VIE")),
                TextInformationFrame("USLT", null, listOf("I wish you the best\nFor the rest of your life"))
            )
        ).single()
        assertEquals("I wish you the best\nFor the rest of your life", (found.lyrics as Lyrics.Plain).text)
    }

    @Test
    fun `tag names count as labels, so a romaji comment sorts as romaji`() {
        val found = lyricsFromMetadata(
            listOf(
                VorbisComment("LYRICS", "伸ばした手は届かない\n君の声が遠くなる"),
                VorbisComment("LYRICS-ROMAJI", "Nobashita te wa todokanai\nKimi no koe ga tooku naru")
            )
        )
        assertEquals(listOf(LyricsKind.ORIGINAL, LyricsKind.ROMAJI), sortEmbedded(found, "1").map { it.first })
    }
}
