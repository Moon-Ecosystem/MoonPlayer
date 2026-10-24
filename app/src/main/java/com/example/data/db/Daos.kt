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

    /** Deletes the playlist together with its songs, which would otherwise be left behind. */
    @Transaction
    suspend fun deletePlaylistAndTracks(playlistId: Long) {
        clearPlaylist(playlistId)
        deletePlaylist(playlistId)
    }

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>

    /** Song count of every playlist that has songs (empty ones have no row). */
    @Query("SELECT playlistId, COUNT(*) AS songs FROM playlist_tracks GROUP BY playlistId")
    fun getTrackCounts(): Flow<List<PlaylistSongCount>>

    /** Position for a song appended to the end of the playlist. */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextSortOrder(playlistId: Long): Int

    @Query("UPDATE playlist_tracks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Rewrites positions 0..n-1 in the given order of playlist_tracks row ids. */
    @Transaction
    suspend fun reorder(rowIdsInOrder: List<Long>) {
        rowIdsInOrder.forEachIndexed { position, id -> setSortOrder(id, position) }
    }
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

    /** Every play since [since] (ms), oldest first, for the listening stats. */
    @Query("SELECT * FROM history WHERE playedAt >= :since ORDER BY playedAt ASC")
    fun getHistorySince(since: Long): Flow<List<HistoryEntity>>

    /** Adds played time to the song's latest play (old plays with unknown time, -1, are left alone). */
    @Query(
        "UPDATE history SET listenedMs = listenedMs + :ms " +
            "WHERE id = (SELECT MAX(id) FROM history WHERE trackId = :trackId) AND listenedMs >= 0"
    )
    suspend fun addListenTime(trackId: String, ms: Long)
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
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun get(trackId: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("UPDATE lyrics SET chosenKind = :kind WHERE trackId = :trackId")
    suspend fun setChosenKind(trackId: String, kind: String)
}

/** When each playlist was last played from; listening stats themselves come from history. */
@Dao
interface TrackStatDao {
    @Query("DELETE FROM playlist_stats")
    suspend fun clearPlaylistStats()

    @Query("SELECT name FROM playlist_stats ORDER BY lastPlayedAt DESC LIMIT 20")
    fun getRecentPlaylistNames(): Flow<List<String>>

    @Query("SELECT * FROM playlist_stats WHERE name = :name")
    suspend fun getPlaylistStat(name: String): PlaylistStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePlaylistStat(stat: PlaylistStatEntity)

    @Transaction
    suspend fun recordPlaylistPlay(name: String) {
        val existing = getPlaylistStat(name)
        insertOrUpdatePlaylistStat(
            PlaylistStatEntity(
                name = name,
                playCount = (existing?.playCount ?: 0) + 1,
                totalTimeMs = existing?.totalTimeMs ?: 0L,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }
}

