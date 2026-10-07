package com.nickspeelman.localjournal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: MoodEntry): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<MoodEntry>)

    @Update
    suspend fun update(entry: MoodEntry): Int

    @Delete
    suspend fun delete(entry: MoodEntry): Int

    @Query("DELETE FROM mood_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC")
    suspend fun getAllEntriesSnapshot(): List<MoodEntry>

    @Query("SELECT COUNT(*) FROM mood_entries")
    suspend fun getEntryCount(): Int

    @Query("SELECT * FROM mood_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Int): MoodEntry?

    @Query("SELECT * FROM mood_entries WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getEntriesSince(startTime: Long): Flow<List<MoodEntry>>

    @Transaction
    suspend fun replaceAll(entries: List<MoodEntry>) {
        deleteAll()
        if (entries.isNotEmpty()) {
            insertAll(entries)
        }
    }
}
