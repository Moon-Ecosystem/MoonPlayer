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
