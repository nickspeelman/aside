package com.nickspeelman.localjournal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MoodEntry::class], version = 2, exportSchema = true)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao

    companion object {
        @Volatile
        private var INSTANCE: MoodDatabase? = null

        /**
         * v2 makes rating nullable so a note-only/malformed check-in can be preserved without
         * inventing a 3/5 mood. Rebuilding the table is required because SQLite cannot alter a
         * column's NOT NULL constraint in place. Every existing row is copied unchanged.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mood_entries_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `rating` INTEGER,
                        `note` TEXT NOT NULL,
                        `hashtags` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `mood_entries_new` (`id`, `rating`, `note`, `hashtags`, `timestamp`)
                    SELECT `id`, `rating`, `note`, `hashtags`, `timestamp` FROM `mood_entries`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `mood_entries`")
                db.execSQL("ALTER TABLE `mood_entries_new` RENAME TO `mood_entries`")
            }
        }

        fun getDatabase(context: Context): MoodDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoodDatabase::class.java,
                    "mood_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
