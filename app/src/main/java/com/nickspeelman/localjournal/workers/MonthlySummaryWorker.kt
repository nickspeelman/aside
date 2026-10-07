package com.nickspeelman.localjournal.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nickspeelman.localjournal.analytics.MonthlyReportAnalytics
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.notifications.MonthlyReportScheduler
import com.nickspeelman.localjournal.notifications.NotificationHelper
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZonedDateTime

class MonthlySummaryWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        val settings = settingsManager.settingsFlow.first()
        val privacy = settingsManager.privacySettingsFlow.first()
        if (!MonthlyReportScheduler.shouldScheduleSeparate(settings)) return Result.success()

        val zoneId = ZoneId.systemDefault()
        val endDate = MonthlyReportScheduler.reportEndDateForExecution(ZonedDateTime.now(zoneId), settings)
        val db = MoodDatabase.getDatabase(applicationContext)
        val entries = MoodRepository(db.moodDao()).allEntries.first()
        val report = MonthlyReportAnalytics.build(entries, settings, endDate, zoneId)
        if (report != null) {
            NotificationHelper(applicationContext).showMonthlyReport(report, settings, privacy)
        }

        // WorkManager has no calendar-month periodic interval, so schedule the next month explicitly.
        MonthlyReportScheduler.scheduleNextAfterExecution(applicationContext, settings)
        return Result.success()
    }
}
