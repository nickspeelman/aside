package com.nickspeelman.localjournal

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nickspeelman.localjournal.data.MoodDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoodDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration1To2PreservesRowsAndAllowsNullableRatings() = runBlocking {
        context.openOrCreateDatabase(TEST_DB, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE mood_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    rating INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    hashtags TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO mood_entries (rating, note, hashtags, timestamp) VALUES (4, 'before migration', '#test', 1234)"
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, MoodDatabase::class.java, TEST_DB)
            .addMigrations(MoodDatabase.MIGRATION_1_2)
            .build()

        try {
            val dao = room.moodDao()
            val migrated = dao.getAllEntriesSnapshot()
            assertEquals(1, migrated.size)
            assertEquals(4, migrated.single().rating)
            assertEquals("before migration", migrated.single().note)
            assertEquals(1, dao.getEntryCount())

            val id = dao.insert(
                migrated.single().copy(
                    id = 0,
                    rating = null,
                    note = "note only",
                    hashtags = "",
                    timestamp = 5678
                )
            )
            assertNull(dao.getEntryById(id.toInt())?.rating)
        } finally {
            room.close()
        }
    }

    companion object {
        private const val TEST_DB = "aside-migration-test.db"
    }
}
