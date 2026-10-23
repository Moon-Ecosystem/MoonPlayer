package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        QueueEntity::class,
        TrackStatEntity::class,
        LibraryTrackEntity::class,
        PlaylistStatEntity::class
    ],
    // Version 4 is the first schema with real user data. Any bump from here needs a Migration
    // added to the builder below; only the pre-release versions 1-3 may be wiped.
    version = 5,
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
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_stats` (`name` TEXT NOT NULL, " +
                        "`playCount` INTEGER NOT NULL, `totalTimeMs` INTEGER NOT NULL, " +
                        "`lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`name`))"
                )
            }
        }

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
                    .addMigrations(MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
