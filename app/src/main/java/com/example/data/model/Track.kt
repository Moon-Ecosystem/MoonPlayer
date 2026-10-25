package com.example.data.model

// "/" or ";" between names, except the slash inside AC/DC.
private val ARTIST_SEPARATOR = Regex("""\s*(?:;|(?<!\bAC)/(?!DC\b))\s*""", RegexOption.IGNORE_CASE)

/**
 * The artists in a song's artist tag. Android joins a file's several artists with "/" (some
 * tags use ";"), so "RADWIMPS/Toaka" is two. "&" and "," are left alone: too many single
 * names contain them ("Simon & Garfunkel", "Earth, Wind & Fire").
 */
fun splitArtists(artist: String): List<String> =
    artist.split(ARTIST_SEPARATOR).filter { it.isNotBlank() }.distinct().ifEmpty { listOf(artist) }

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
