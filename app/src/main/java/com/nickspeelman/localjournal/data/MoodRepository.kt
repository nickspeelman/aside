package com.nickspeelman.localjournal.data

import kotlinx.coroutines.flow.Flow

class MoodRepository(private val moodDao: MoodDao) {
    val allEntries: Flow<List<MoodEntry>> = moodDao.getAllEntries()

    suspend fun insert(entry: MoodEntry) {
        moodDao.insert(entry)
    }

    fun getEntriesSince(startTime: Long): Flow<List<MoodEntry>> {
        return moodDao.getEntriesSince(startTime)
    }
}
