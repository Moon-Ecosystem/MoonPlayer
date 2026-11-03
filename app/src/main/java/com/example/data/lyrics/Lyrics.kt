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
    val kinds = lyricsKinds(fitting.map { it.syncedLyrics!! }, title)
    return fitting.indices.groupBy { kinds[it] }.mapValues { (_, versions) ->
        versions.map { fitting[it] }
            .minBy { if (trackDurationSec <= 0) 0.0 else abs(it.durationSec - trackDurationSec) }.syncedLyrics!!
    }
}

/**
 * The kind of each of a song's lyrics [texts]. A song counts as non-Latin when its [title] is, or
 * any text is in another script; only then are Latin texts sorted into romaji and translation.
 * [labels] (a tag's description or heading line) win when they say "romaji" or "translation".
 */
fun lyricsKinds(texts: List<String>, title: String, labels: List<String> = emptyList()): List<LyricsKind> {
    val nonLatinSong = (title.any { it.isLetter() } && latinShare(title) < 0.5) || texts.any { latinShare(it) < 0.5 }
    return texts.mapIndexed { i, text ->
        val label = labels.getOrNull(i).orEmpty().lowercase()
        when {
            ROMAJI_LABELS.any { it in label } -> LyricsKind.ROMAJI
            TRANSLATION_LABELS.any { it in label } -> LyricsKind.TRANSLATION
            !nonLatinSong || latinShare(text) < 0.5 -> LyricsKind.ORIGINAL
            looksLikeRomaji(text) -> LyricsKind.ROMAJI
            else -> LyricsKind.TRANSLATION
        }
    }
}

// Genius heads its romanisations "…羅馬拼音歌詞" and translations "…英語翻訳" / "English Translation".
private val ROMAJI_LABELS = listOf("roma", "羅馬", "ローマ字")
private val TRANSLATION_LABELS = listOf("translat", "翻訳", "翻譯", "번역")

/** Synced when the text has timestamps, else plain; null for blank text. */
fun lyricsFromText(text: String): Lyrics? =
    parseLrc(text)?.let { Lyrics.Synced(it) } ?: text.takeIf { it.isNotBlank() }?.let { Lyrics.Plain(it.trim()) }

/**
 * A heading like "[YOASOBI「群青」歌詞]" that Genius puts before the words: a label for the
 * version, not something sung. Null when the text doesn't start with one.
 */
fun lyricsHeading(text: String): String? =
    text.trimStart().lineSequence().firstOrNull()?.trim()?.takeIf { HEADING.matches(it) }

private val HEADING = Regex("""^\[[^\]]*(歌詞|Lyrics|Romaniz|Translation)[^\]]*]$""", RegexOption.IGNORE_CASE)

/** Plain text lyrics without a [lyricsHeading]. */
fun withoutHeading(text: String): String =
    if (lyricsHeading(text) == null) text else text.trimStart().substringAfter('\n', "").trimStart('\n')


/**
 * A file's lyrics tags as one version per kind, in kind order. Plain text loses its Genius
 * heading, which (with the tag's description) helps tell romaji and translations apart; when a
 * kind turns up twice, synced beats plain, then the first tag wins.
 */
fun sortEmbedded(tags: List<EmbeddedLyrics>, title: String): List<Pair<LyricsKind, Lyrics>> {
    val cleaned = tags.map { tag ->
        when (val l = tag.lyrics) {
            is Lyrics.Synced -> Triple(l, l.lines.joinToString("\n") { it.text }, tag.description)
            is Lyrics.Plain -> {
                val heading = lyricsHeading(l.text).orEmpty()
                val body = withoutHeading(l.text).trim()
                Triple(Lyrics.Plain(body), body, tag.description + " " + heading)
            }
        }
    }.filter { it.second.isNotBlank() }
    val kinds = lyricsKinds(cleaned.map { it.second }, title, cleaned.map { it.third })
    return cleaned.indices.groupBy { kinds[it] }
        .mapValues { (_, ids) -> (ids.firstOrNull { cleaned[it].first is Lyrics.Synced } ?: ids.first()).let { cleaned[it].first } }
        .toSortedMap()
        .toList()
}

/**
 * The order sources are tried in until the user changes it in Settings › Lyrics. It keeps 0.1's
 * behaviour (a .lrc, else LRCLIB); lyrics stored in the song file, new since, come after.
 */
val DEFAULT_LYRICS_SOURCE_ORDER = listOf(LyricsSource.LRC_FILE, LyricsSource.LRCLIB, LyricsSource.EMBEDDED)

/**
 * Whether LRCLIB is worth asking automatically: unless a source ranked above it in [order]
 * already has synced lyrics. With the default order that's "no synced .lrc", as in 0.1.
 */
fun shouldAskLrclib(syncedSources: Set<LyricsSource>, order: List<LyricsSource>): Boolean {
    val above = order.takeWhile { it != LyricsSource.LRCLIB }
    return above.none { it in syncedSources }
}

/**
 * The automatic pick among one kind's [candidates]: a synced one first when [preferSynced], then
 * the user's source [order]. Null when there are none.
 */
fun autoSource(candidates: Map<LyricsSource, Lyrics>, order: List<LyricsSource>, preferSynced: Boolean): LyricsSource? {
    fun rank(s: LyricsSource) = order.indexOf(s).takeIf { it >= 0 } ?: (order.size + s.ordinal)
    return candidates.keys.minWithOrNull(
        compareBy<LyricsSource>({ if (preferSynced && candidates[it] !is Lyrics.Synced) 1 else 0 }, { rank(it) })
    )
}

/** What a song remembers: the kind it opens on, and a source picked by hand per kind. */
data class LyricsChoice(val kind: LyricsKind? = null, val sources: Map<LyricsKind, LyricsSource> = emptyMap())

/** "ROMAJI|ORIGINAL=LRC_FILE|ROMAJI=LRCLIB": the kind, then each hand-picked source. */
fun encodeChoice(c: LyricsChoice): String =
    (listOf(c.kind?.name.orEmpty()) + c.sources.map { (k, s) -> "${k.name}=${s.name}" }).joinToString("|")

/** Also reads what 0.2 saved: "ROMAJI" (an LRCLIB version) or "EMBEDDED:ROMAJI". */
fun parseChoice(saved: String?): LyricsChoice {
    if (saved.isNullOrBlank()) return LyricsChoice()
    fun kindOf(s: String) = runCatching { LyricsKind.valueOf(s) }.getOrNull()
    fun sourceOf(s: String) = runCatching { LyricsSource.valueOf(s) }.getOrNull()
    val parts = saved.split('|')
    val head = parts.first()
    val sources = mutableMapOf<LyricsKind, LyricsSource>()
    val kind = if (':' in head) {
        val k = kindOf(head.substringAfter(':'))
        val s = sourceOf(head.substringBefore(':'))
        if (k != null && s != null) sources[k] = s
        k
    } else kindOf(head)
    parts.drop(1).forEach { p ->
        val k = kindOf(p.substringBefore('='))
        val s = sourceOf(p.substringAfter('='))
        if (k != null && s != null) sources[k] = s
    }
    return LyricsChoice(kind, sources)
}

/** Saved as names, "LRC_FILE,EMBEDDED,LRCLIB"; missing or unknown names fall back to the default order. */
fun parseSourceOrder(saved: String?): List<LyricsSource> {
    val named = saved.orEmpty().split(',').mapNotNull { n -> LyricsSource.entries.firstOrNull { it.name == n.trim() } }.distinct()
    return named + DEFAULT_LYRICS_SOURCE_ORDER.filter { it !in named }
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
