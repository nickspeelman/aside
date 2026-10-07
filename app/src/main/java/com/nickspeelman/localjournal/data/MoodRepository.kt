package com.nickspeelman.localjournal.data

import kotlinx.coroutines.flow.Flow

class MoodRepository(
    private val moodDao: MoodDao,
    private val manualBackupManager: ManualBackupManager? = null
) {
    val allEntries: Flow<List<MoodEntry>> = moodDao.getAllEntries()

    suspend fun insert(entry: MoodEntry): Long {
        val id = moodDao.insert(entry)
        manualBackupManager?.recordInsert()
        return id
    }
    suspend fun update(entry: MoodEntry) { moodDao.update(entry); manualBackupManager?.recordOtherMutation() }
    suspend fun delete(entry: MoodEntry) { moodDao.delete(entry); manualBackupManager?.recordOtherMutation() }
    suspend fun deleteAll() { moodDao.deleteAll(); manualBackupManager?.recordOtherMutation() }
    suspend fun snapshot(): List<MoodEntry> = moodDao.getAllEntriesSnapshot()
    suspend fun getEntryById(id: Int): MoodEntry? = moodDao.getEntryById(id)
    suspend fun replaceAll(entries: List<MoodEntry>) { moodDao.replaceAll(entries) }
    fun getEntriesSince(startTime: Long): Flow<List<MoodEntry>> = moodDao.getEntriesSince(startTime)
}
