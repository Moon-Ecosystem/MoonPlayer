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
    val mimeType: String = "audio/mpeg",
    val albumArtUri: String? = null,
    val folderPath: String = "",
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTime: Long = 0L,
    val bitrateKbps: Int = 320,
    val sampleRateHz: Int = 44100
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
