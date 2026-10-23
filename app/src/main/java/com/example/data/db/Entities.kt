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

