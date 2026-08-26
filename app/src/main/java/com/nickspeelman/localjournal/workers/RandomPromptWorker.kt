package com.nickspeelman.localjournal.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

        NotificationHelper(applicationContext).showMoodPrompt()

        // The current window has now produced its prompt, so advance to a later window.
        scheduleNext(
            context = applicationContext,
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE,
            includeCurrentWindow = false
        )

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "random_prompt_unique"
        private const val WORK_TAG = "random_prompt"

        /**
         * Called during ordinary app startup. KEEP is important: opening the app must not
         * cancel and postpone a prompt that was already waiting in WorkManager.
         */
        suspend fun ensureScheduled(context: Context) {
            scheduleNext(
                context = context,
                policy = ExistingWorkPolicy.KEEP,
                includeCurrentWindow = true
            )
        }

        /**
         * Called after the user explicitly changes scheduling settings. The previous schedule
         * is intentionally replaced, using the settings that have already been persisted.
         */
        suspend fun reschedule(context: Context) {
            scheduleNext(
                context = context,
                policy = ExistingWorkPolicy.REPLACE,
                includeCurrentWindow = true
            )
        }

        private suspend fun scheduleNext(
            context: Context,
            policy: ExistingWorkPolicy,
            includeCurrentWindow: Boolean
        ) {
            val appContext = context.applicationContext
            val workManager = WorkManager.getInstance(appContext)
            val settings = SettingsManager(appContext).settingsFlow.first()

            if (settings.isPaused) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val delayMinutes = NotificationScheduler.calculateNextDelayMinutes(
                nowMillis = System.currentTimeMillis(),
                settings = settings,
                includeCurrentWindow = includeCurrentWindow
            )

            val workRequest = OneTimeWorkRequestBuilder<RandomPromptWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                workRequest
            )
        }
    }
}
