package com.nickspeelman.localjournal.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nickspeelman.localjournal.data.ManualBackupManager
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

class BackupReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val manager = ManualBackupManager(applicationContext)
        val state = manager.current()
        if (!state.reminderEnabled) return Result.success()
        val count = MoodDatabase.getDatabase(applicationContext).moodDao().getEntryCount()
        if (count == 0 || (state.hasSuccessfulBackup && state.isCurrent)) return Result.success()
        val now = System.currentTimeMillis()
        val anchor = state.lastReminderAt ?: state.lastSuccessfulBackupAt ?: state.reminderAnchorAt ?: now
        if (now - anchor < TimeUnit.DAYS.toMillis(state.reminderIntervalDays.toLong())) return Result.success()
        NotificationHelper(applicationContext).showBackupReminder()
        manager.markReminderSent(now)
        return Result.success()
    }
}
