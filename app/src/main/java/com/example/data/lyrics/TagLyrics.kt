package com.example.data.lyrics

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.metadata.id3.TextInformationFrame

// LYRICS, UNSYNCEDLYRICS, SYNCEDLYRICS, "UNSYNCED LYRICS", LYRICS-ROMAJI…; never LYRICIST.
private val LYRICS_KEY = Regex("""^((UN)?SYNCED[ _]?)?LYRICS([ _:-].*)?$""", RegexOption.IGNORE_CASE)

/**
 * Lyrics among the tags Media3 decoded for a non-MP3 file: Vorbis comments (FLAC, Ogg, Opus)
 * and an MP4's ©lyr atom, which Media3 hands over as a "USLT" text frame. MP3s go through
 * [readId3Lyrics], since Media3 doesn't decode their lyrics frames.
 */
@OptIn(UnstableApi::class)
fun lyricsFromMetadata(entries: List<Metadata.Entry>): List<EmbeddedLyrics> = entries.mapNotNull { e ->
    val (label, text) = when {
        e is VorbisComment && LYRICS_KEY.matches(e.key.trim()) -> e.key to e.value
        e is TextInformationFrame && e.id == "USLT" -> e.description.orEmpty() to e.values.joinToString("\n")
        else -> return@mapNotNull null
    }
    lyricsFromText(text.replace("\r\n", "\n").replace('\r', '\n'))?.let { EmbeddedLyrics(label, it) }
}
