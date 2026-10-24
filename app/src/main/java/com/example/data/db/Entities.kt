package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Result row of [PlaylistDao.getTrackCounts]. */
data class PlaylistSongCount(val playlistId: Long, val songs: Int)

@Entity(
    tableName = "playlist_tracks",
    indices = [Index(value = ["playlistId"]), Index(value = ["trackId"])]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val trackId: String,
    val sortOrder: Int,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtUri: String? = null
)

/** Last scan of the selected folder, so relaunch shows the library without re-reading every file. */
@Entity(tableName = "library_tracks")
data class LibraryTrackEntity(
    @PrimaryKey
    val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folderPath: String,
    val dateAdded: Long,
    val mimeType: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumArtUri: String? = null
)

@Entity(tableName = "playback_queue")
data class QueueEntity(
    @PrimaryKey
    val position: Int,
    val trackId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtUri: String? = null
)

/**
 * Lyrics found for a track, kept so each song is looked up once: the synced version of each
 * kind LRCLIB had, plain text as a fallback, and which kind the user picked for this song.
 * All texts null records "nothing found" so the lookup isn't repeated until the user retries.
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey
    val trackId: String,
    val originalLrc: String?,
    val romajiLrc: String?,
    val translationLrc: String?,
    val plain: String?,
    val chosenKind: String?,
    val source: String,
    val fetchedAt: Long
)

/** Songs played from a playlist (user or folder), keyed by the playlist's name. */
@Entity(tableName = "playlist_stats")
data class PlaylistStatEntity(
    @PrimaryKey
    val name: String,
    val playCount: Int,
    val totalTimeMs: Long,
    val lastPlayedAt: Long
)

@Entity(tableName = "track_stats")
data class TrackStatEntity(
    @PrimaryKey
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val playCount: Int = 1,
    val totalTimeMs: Long = 0L,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

