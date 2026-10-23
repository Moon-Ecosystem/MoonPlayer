package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_tracks")
    suspend fun getAll(): List<LibraryTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<LibraryTrackEntity>)

    @Query("DELETE FROM library_tracks")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(tracks: List<LibraryTrackEntity>) {
        clear()
        insertAll(tracks)
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT trackId FROM favorites")
    fun getAllFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHistory(history: HistoryEntity): Long

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM playback_queue ORDER BY position ASC")
    fun getSavedQueue(): Flow<List<QueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()

    @Transaction
    suspend fun replaceQueue(items: List<QueueEntity>) {
        clearQueue()
        insertQueueItems(items)
    }
}

@Dao
interface TrackStatDao {
    @Query("SELECT * FROM track_stats ORDER BY playCount DESC LIMIT 20")
    fun getTopPlayedTracks(): Flow<List<TrackStatEntity>>

    @Query("SELECT * FROM track_stats ORDER BY totalTimeMs DESC LIMIT 20")
    fun getMostListenedTracks(): Flow<List<TrackStatEntity>>

    @Query("SELECT SUM(playCount) FROM track_stats")
    fun getTotalPlays(): Flow<Int?>

    @Query("SELECT SUM(totalTimeMs) FROM track_stats")
    fun getTotalListenTimeMs(): Flow<Long?>

    @Query("SELECT * FROM track_stats WHERE trackId = :trackId")
    suspend fun getStatForTrack(trackId: String): TrackStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStat(stat: TrackStatEntity)

    @Transaction
    suspend fun recordPlay(trackId: String, title: String, artist: String, album: String, durationMs: Long) {
        val existing = getStatForTrack(trackId)
        if (existing != null) {
            insertOrUpdateStat(
                existing.copy(
                    playCount = existing.playCount + 1,
                    totalTimeMs = existing.totalTimeMs + durationMs,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
        } else {
            insertOrUpdateStat(
                TrackStatEntity(
                    trackId = trackId,
                    title = title,
                    artist = artist,
                    album = album,
                    playCount = 1,
                    totalTimeMs = durationMs,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

