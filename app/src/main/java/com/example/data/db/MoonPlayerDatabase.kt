package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        QueueEntity::class,
        TrackStatEntity::class,
        LibraryTrackEntity::class
    ],
    // Version 4 is the first schema with real user data. Any bump from here needs a Migration
    // added to the builder below; only the pre-release versions 1-3 may be wiped.
    version = 4,
    exportSchema = false
)
abstract class MoonPlayerDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
    abstract fun trackStatDao(): TrackStatDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var INSTANCE: MoonPlayerDatabase? = null

        fun getInstance(context: Context): MoonPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MoonPlayerDatabase::class.java,
                    "moon_player.db"
                )
                    // A missing migration now crashes on upgrade instead of silently deleting
                    // playlists, favourites and stats.
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
