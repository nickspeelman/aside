package com.nickspeelman.localjournal.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.notifications.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class WeeklySummaryWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = MoodDatabase.getDatabase(applicationContext)
        val repository = MoodRepository(db.moodDao())
        
        val oneWeekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val entries = repository.getEntriesSince(oneWeekAgo).first()

        if (entries.isNotEmpty()) {
            val avgRating = entries.map { it.rating }.average()
            val entryCount = entries.size
            
            val summaryText = "You recorded $entryCount entries this week with an average mood of ${"%.1f".format(avgRating)}/10."
            
            NotificationHelper(applicationContext).showWeeklySummary(summaryText)
        }

        return Result.success()
    }
}
