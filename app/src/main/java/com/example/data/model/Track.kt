package com.example.data.model

// Every place a tag might join two artists: "/", ";" or "|" (not the slash in AC/DC), "," or the
// Japanese "、", and, between spaces, "&", "and", "feat.", "ft.", "featuring", "x", "×".
private val ARTIST_SEPARATOR = Regex(
    """\s*(?:;|(?<!\bAC)/(?!DC\b)|[|｜]|,|、)\s*|\s+(?:&|and|featuring|feat\.?|ft\.?|x|×)\s+""",
    RegexOption.IGNORE_CASE
)
// Always split (Android joins a file's several artists with "/"), and split when commas are on.
private val HARD_SEPARATORS = setOf("/", ";", "|", "｜")
// A credit's role before the name: "Back Up Vocal : Shadab Faridi", "Music: A.R. Rahman". The
// colon needs a space after it (or is the full-width "："), so "Re:Zero" stays whole.
private val ROLE_PREFIX = Regex("""^[^:：]{1,40}?\s*(?::\s+|：\s*)(?=\S)""")
private val COMMAS = setOf(",", "、")

// Well-known names with a comma in them, kept whole even beside other artists.
private val COMMA_NAMES = setOf(
    "earth, wind & fire", "crosby, stills, nash & young", "crosby, stills & nash",
    "emerson, lake & palmer", "blood, sweat & tears", "peter, paul and mary",
    "tyler, the creator", "the good, the bad & the queen"
)

/** An artist tag cut at every separator: [names] with the [separators] between them (one fewer). */
data class ArtistTag(val names: List<String>, val separators: List<String>)

/**
 * How to split artist tags: [commas] (Settings › Library) and the user's own [fixes], each a full
 * tag mapped to one split-or-not per separator, from an artist page's "How this name splits".
 */
data class ArtistSplitRules(val commas: Boolean = true, val fixes: Map<String, List<Boolean>> = emptyMap())

fun artistTag(tag: String): ArtistTag {
    val names = mutableListOf<String>()
    val separators = mutableListOf<String>()
    var separator = ""
    var start = 0
    // A blank piece (", , " or a leading separator) is dropped with the separator before it.
    fun take(end: Int) {
        val name = tag.substring(start, end).trim().replaceFirst(ROLE_PREFIX, "")
        if (name.isEmpty()) return
        if (names.isNotEmpty()) separators += separator
        names += name
    }
    ARTIST_SEPARATOR.findAll(tag).forEach { m ->
        take(m.range.first)
        separator = m.value.trim()
        start = m.range.last + 1
    }
    take(tag.length)
    return ArtistTag(names, separators)
}

private fun joinWith(separator: String) = when (separator) {
    ",", ";" -> "$separator "
    "/", "、", "｜" -> separator
    else -> " $separator "
}

/** names[from..to] as one name, with the separators between them. */
private fun ArtistTag.joined(from: Int, to: Int): String =
    (from + 1..to).fold(names[from]) { acc, i -> acc + joinWith(separators[i - 1]) + names[i] }

/**
 * Which separators split when the user hasn't chosen: "/" and ";" always, commas when [commas]
 * is on, except inside a well-known name ([COMMA_NAMES]) or a band named as a list of single words
 * ending in "& X" / "and X" ("Earth, Wind & Fire"). "&", "feat." and the rest never: too many
 * single names contain them ("Simon & Garfunkel").
 */
fun autoSplits(tag: ArtistTag, commas: Boolean): List<Boolean> {
    val split = tag.separators.map { it in HARD_SEPARATORS || (commas && it in COMMAS) }.toMutableList()
    if (!commas) return split
    var i = 0
    while (i < tag.names.size) {
        // The longest run of names from here that spells a well-known name.
        val end = (tag.names.size - 1 downTo i + 1).firstOrNull { tag.joined(i, it).lowercase() in COMMA_NAMES }
        if (end != null) (i until end).forEach { split[it] = false }
        i = (end ?: i) + 1
    }
    // Each stretch between "/" and ";": commas, then one "&"/"and", with single words before it.
    var from = 0
    for (to in tag.names.indices) {
        if (to < tag.separators.size && tag.separators[to] !in HARD_SEPARATORS) continue
        val seps = tag.separators.subList(from, to)
        val bandList = seps.size >= 2 && seps.dropLast(1).all { it in COMMAS } &&
            seps.last().lowercase() in setOf("&", "and") &&
            tag.names.subList(from, to - 1).all { ' ' !in it }
        if (bandList) (from until to).forEach { split[it] = false }
        from = to + 1
    }
    return split
}

/** The splits used for [tag]: the user's own choice for it, else [autoSplits]. */
fun ArtistSplitRules.splitsFor(tag: String, cut: ArtistTag = artistTag(tag)): List<Boolean> =
    fixes[tag.trim()]?.takeIf { it.size == cut.separators.size } ?: autoSplits(cut, commas)

/** The artists [cut] makes with [splits]: names joined wherever a separator doesn't split. */
fun artistsOf(cut: ArtistTag, splits: List<Boolean>): List<String> {
    if (cut.names.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    var from = 0
    for (i in cut.separators.indices) {
        if (splits[i]) {
            out += cut.joined(from, i)
            from = i + 1
        }
    }
    out += cut.joined(from, cut.names.size - 1)
    return out
}

/**
 * The artists in a song's artist tag: "RADWIMPS/Toaka" and "A.R. Rahman, Lata Mangeshkar" are two
 * each, "Earth, Wind & Fire" and "Simon & Garfunkel" one; see [autoSplits] and [ArtistSplitRules].
 */
fun splitArtists(artist: String, rules: ArtistSplitRules = ArtistSplitRules()): List<String> {
    val cut = artistTag(artist)
    return artistsOf(cut, rules.splitsFor(artist, cut)).distinct().ifEmpty { listOf(artist) }
}

// A version note: starts like "From THE FIRST TAKE" / "feat. X" / "Live at", or ends like
// "Original Version" / "movie ver." / "2011 Remaster" / "Radio Edit".
private val VERSION_NOTE = Regex(
    """^(from|feat\.?|ft\.|live|remaster(ed)?)(\s.*)?|.*\b(version|ver\.?|remix|mix|edit|remaster(ed)?|live|acoustic|instrumental|tv size|cover|demo|mono|stereo|karaoke|off vocal)$""",
    RegexOption.IGNORE_CASE
)
private val TRAILING_BRACKETS = Regex("""\s*[(\[（【]([^()\[\]（）【】]+)[)\]）】]$""")
private val TRAILING_DASH = Regex("""\s+[-–—]\s+([^-–—]+)$""")
private val TRAILING_FEAT = Regex("""\s+((feat\.?|ft\.)\s.+)$""", RegexOption.IGNORE_CASE)

/**
 * Splits version notes off the end of a title for the player, e.g. "ウィーアー！ - From THE FIRST
 * TAKE" -> ("ウィーアー！", "From THE FIRST TAKE"). Only recognised notes at the very end are
 * split, so "Hello - Goodbye" stays whole; several notes are joined with " · ".
 */
fun splitTitle(title: String): Pair<String, String?> {
    var rest = title.trim()
    val notes = ArrayDeque<String>()
    while (true) {
        val match = TRAILING_FEAT.find(rest)
            ?: listOf(TRAILING_BRACKETS, TRAILING_DASH).firstNotNullOfOrNull { re ->
                re.find(rest)?.takeIf { VERSION_NOTE.matches(it.groupValues[1].trim()) }
            }
            ?: break
        val remaining = rest.substring(0, match.range.first).trim()
        if (remaining.isEmpty()) break
        notes.addFirst(match.groupValues[1].trim())
        rest = remaining
    }
    return rest to notes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

data class Track(
    val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    // Empty/0 mean "not known" (e.g. tracks rebuilt from playlists); the UI hides them.
    val mimeType: String = "",
    val albumArtUri: String? = null,
    val folderPath: String = "",
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTime: Long = 0L,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0
) {
    val formattedDuration: String
        get() = formatDuration(durationMs)

    companion object {
        fun formatDuration(ms: Long): String {
            if (ms <= 0) return "00:00"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
