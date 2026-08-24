package com.nickspeelman.localjournal.workers

import android.content.Context
import androidx.work.*
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.notifications.NotificationHelper
import com.nickspeelman.localjournal.notifications.NotificationScheduler
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class RandomPromptWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        val settings = settingsManager.settingsFlow.first()

        if (settings.isPaused) {
            return Result.success()
        }

        val notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.showMoodPrompt()

        // Schedule next random prompt based on settings
        scheduleNext(applicationContext)

        return Result.success()
    }

    companion object {
        suspend fun scheduleNext(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val settingsManager = SettingsManager(context)
            val settings = settingsManager.settingsFlow.first()
            
            if (settings.isPaused) {
                workManager.cancelUniqueWork("random_prompt_unique")
                return
            }

            val delayMinutes = NotificationScheduler.calculateNextDelayMinutes(
                System.currentTimeMillis(),
                settings
            )
            
            val workRequest = OneTimeWorkRequestBuilder<RandomPromptWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag("random_prompt")
                .build()

            workManager.enqueueUniqueWork(
                "random_prompt_unique",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
