package com.nickspeelman.localjournal.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.workers.BackupReminderWorker
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object BackupReminderScheduler {
    private const val NAME = "manual_backup_reminder_check"

    suspend fun ensureScheduled(context: Context) {
        val enabled = SettingsManager(context.applicationContext)
            .settingsFlow.first().backupReminderEnabled
        sync(context, enabled)
    }

    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(1, TimeUnit.DAYS).build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
