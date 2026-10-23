package com.example.data.lyrics

import kotlin.math.abs

/** One timed line of synced lyrics; [text] is blank for instrumental gaps. */
data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
}

private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
// Word-level timings of "enhanced" LRC (<00:12.34>); line sync only needs them gone.
private val WORD_TIMESTAMP = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

/**
 * Parses LRC text into time-ordered lines, or null when it has no timestamps (plain text).
 * Handles several timestamps on one line, 1-3 digit fractions and the [offset:] header
 * (positive shows lyrics earlier). Tags like [ar:] and [ti:] are skipped.
 */
fun parseLrc(lrc: String): List<LyricLine>? {
    val offsetMs = OFFSET.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val lines = mutableListOf<LyricLine>()
    for (raw in lrc.removePrefix("﻿").lineSequence()) {
        var rest = raw.trim()
        val times = mutableListOf<Long>()
        while (true) {
            val m = TIMESTAMP.matchAt(rest, 0) ?: break
            val (min, sec, frac) = m.destructured
            val fracMs = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.toLong()
            }
            times += min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs
            rest = rest.substring(m.range.last + 1).trimStart()
        }
        val text = rest.replace(WORD_TIMESTAMP, "").trim()
        times.forEach { lines += LyricLine((it - offsetMs).coerceAtLeast(0), text) }
    }
    return lines.sortedBy { it.timeMs }.takeIf { it.isNotEmpty() }
}

/** Index of the line being sung at [positionMs], or -1 before the first line. */
fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}

/** One LRCLIB record, reduced to what the choice needs. */
data class LrclibRecord(val syncedLyrics: String?, val plainLyrics: String?, val durationSec: Double)

/** Share of the lyric letters (timestamps aside) that are Latin script, 0..1. */
fun latinShare(lyrics: String): Double {
    val letters = lyrics.replace(TIMESTAMP, "").filter { it.isLetter() }
    if (letters.isEmpty()) return 0.0
    return letters.count { it.code < 0x250 }.toDouble() / letters.length
}

/** What a lyrics version is, relative to the song: its own words, a romanisation, or a translation. */
enum class LyricsKind(val label: String) { ORIGINAL("Original"), ROMAJI("Romaji"), TRANSLATION("Translation") }

// One Japanese syllable: optional doubled consonant (kk, tt, tch...), consonant, y-glide, vowel; or "n".
private val ROMAJI_WORD = Regex("""^(?:(?:([kstpgdbmc])(?=\1)|t(?=ch))?(?:ch|sh|ts|[kgsztdnhbpmyrwfjv])?y?[aiueo]|n)+$""")

/**
 * Whether Latin-script lyrics read as romanised Japanese rather than a translation: romaji is
 * built from Japanese syllables ("tokete yuku"), English is full of words that can't be
 * ("and", "night"). J-pop romaji keeps some English phrases, hence the 75% bar.
 */
fun looksLikeRomaji(lyrics: String): Boolean {
    val words = java.text.Normalizer.normalize(lyrics.replace(TIMESTAMP, ""), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "") // ō -> o
        .lowercase()
        .split(Regex("[^a-z]+"))
        .filter { it.isNotEmpty() }
    if (words.size < 4) return false
    return words.count { ROMAJI_WORD.matches(it) }.toDouble() / words.size >= 0.75
}

/**
 * The best synced version of each kind: per kind, the one whose recording length is closest to
 * the file's, within [toleranceSec] (other lengths are edits, live takes or shorts that drift out
 * of sync; an unknown length, 0, accepts any). A song counts as non-Latin when its [title] is,
 * or any fitting version is written in another script; only then are Latin versions sorted into
 * romaji and translation; for a Latin-script song every version is its original.
 */
fun pickLyricsVersions(
    records: List<LrclibRecord>,
    trackDurationSec: Double,
    title: String = "",
    toleranceSec: Double = 3.0
): Map<LyricsKind, String> {
    val fitting = records.filter {
        !it.syncedLyrics.isNullOrBlank() &&
            (trackDurationSec <= 0 || abs(it.durationSec - trackDurationSec) <= toleranceSec)
    }
    val nonLatinSong = (title.any { it.isLetter() } && latinShare(title) < 0.5) ||
        fitting.any { latinShare(it.syncedLyrics!!) < 0.5 }
    fun kindOf(lrc: String) = when {
        !nonLatinSong || latinShare(lrc) < 0.5 -> LyricsKind.ORIGINAL
        looksLikeRomaji(lrc) -> LyricsKind.ROMAJI
        else -> LyricsKind.TRANSLATION
    }
    return fitting.groupBy { kindOf(it.syncedLyrics!!) }.mapValues { (_, versions) ->
        versions.minBy { if (trackDurationSec <= 0) 0.0 else abs(it.durationSec - trackDurationSec) }.syncedLyrics!!
    }
}

/**
 * Which version to show: the one picked for this song, else the preferred script, else the
 * original, romaji, translation in that order. A translation is never chosen over real lyrics.
 */
fun chooseLyricsKind(available: Set<LyricsKind>, chosenForSong: LyricsKind?, preferRomaji: Boolean): LyricsKind? =
    chosenForSong?.takeIf { it in available }
        ?: LyricsKind.ROMAJI.takeIf { preferRomaji && it in available }
        ?: listOf(LyricsKind.ORIGINAL, LyricsKind.ROMAJI, LyricsKind.TRANSLATION).firstOrNull { it in available }

/** Languages whose own script lyrics are written in; for every other phone language, prefer Latin. */
private val NON_LATIN_SCRIPT_LANGUAGES = setOf("ja", "zh", "ko", "ru", "uk", "el", "ar", "he", "hi", "th", "bn", "ta")

fun prefersLatinLyrics(phoneLanguage: String): Boolean = phoneLanguage.lowercase() !in NON_LATIN_SCRIPT_LANGUAGES

/**
 * Document ID of a same-named .lrc beside an audio file, for path-style IDs such as
 * "primary:Music/song.mp3"; null when the ID isn't a path (no extension to swap).
 */
fun siblingLrcDocumentId(audioDocumentId: String): String? {
    val name = audioDocumentId.substringAfterLast('/').substringAfterLast(':')
    if (!name.contains('.')) return null
    return audioDocumentId.substringBeforeLast('.') + ".lrc"
}
