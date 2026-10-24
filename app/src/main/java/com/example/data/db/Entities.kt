package com.example.data.db

import androidx.room.ColumnInfo
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
    val sampleRateHz: Int,
    val albumArtUri: String? = null
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
    val albumArtUri: String? = null,
    // Time actually played; -1 for plays recorded before this was tracked (database version 9).
    @ColumnInfo(defaultValue = "0")
    val listenedMs: Long = 0L,
    // The playlist (yours or a folder) it was played from, if any.
    val playlist: String? = null
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

/**
 * When each playlist (user or folder) was last played from, keyed by name, for the drawer's
 * order. Listening stats come from history; totalTimeMs is no longer updated.
 */
@Entity(tableName = "playlist_stats")
data class PlaylistStatEntity(
    @PrimaryKey
    val name: String,
    val playCount: Int,
    val totalTimeMs: Long,
    val lastPlayedAt: Long
)

