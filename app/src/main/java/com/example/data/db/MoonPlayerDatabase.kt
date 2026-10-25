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
        LibraryTrackEntity::class,
        PlaylistStatEntity::class,
        LyricsEntity::class
    ],
    // Version 4 is the first schema with real user data. Any bump from here needs a Migration
    // added to the builder below; only the pre-release versions 1-3 may be wiped.
    version = 11,
    exportSchema = false
)
abstract class MoonPlayerDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
    abstract fun trackStatDao(): TrackStatDao
    abstract fun libraryDao(): LibraryDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lyrics` (`trackId` TEXT NOT NULL, " +
                        "`syncedLrc` TEXT, `plain` TEXT, `source` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`trackId`))"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_stats` (`name` TEXT NOT NULL, " +
                        "`playCount` INTEGER NOT NULL, `totalTimeMs` INTEGER NOT NULL, " +
                        "`lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`name`))"
                )
            }
        }

        // Version 6 saved one lyrics version without saying which kind (it could be a
        // translation), so the cache is rebuilt rather than migrated; songs re-fetch on open.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `lyrics`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lyrics` (`trackId` TEXT NOT NULL, " +
                        "`originalLrc` TEXT, `romajiLrc` TEXT, `translationLrc` TEXT, `plain` TEXT, " +
                        "`chosenKind` TEXT, `source` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`trackId`))"
                )
            }
        }

        // The folder's cover image (cover.jpg etc.) for each song.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `library_tracks` ADD COLUMN `albumArtUri` TEXT")
            }
        }

        // Deleting a playlist used to leave its songs in playlist_tracks; drop those leftovers.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM `playlist_tracks` WHERE `playlistId` NOT IN (SELECT `id` FROM `playlists`)")
            }
        }

        // Listening stats now come from history: each play keeps the time actually played and the
        // playlist it came from. Older plays never recorded their time, so they're marked -1
        // (unknown) and only count as plays. The per-song totals table it replaces is dropped.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `history` ADD COLUMN `listenedMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `history` ADD COLUMN `playlist` TEXT")
                db.execSQL("UPDATE `history` SET `listenedMs` = -1")
                db.execSQL("DROP TABLE IF EXISTS `track_stats`")
            }
        }

        // Playlists get a description, a cover and their own equalizer preset.
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `coverUri` TEXT")
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `eqPreset` TEXT")
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
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
