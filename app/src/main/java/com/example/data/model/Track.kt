package com.example.data.model

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
