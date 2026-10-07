package com.nickspeelman.localjournal.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nickspeelman.localjournal.analytics.MonthlyReportAnalytics
import com.nickspeelman.localjournal.analytics.WeeklyReportAnalytics
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.notifications.MonthlyReportScheduler
import com.nickspeelman.localjournal.notifications.NotificationHelper
import com.nickspeelman.localjournal.notifications.WeeklyReportScheduler
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklySummaryWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = MoodDatabase.getDatabase(applicationContext)
        val repository = MoodRepository(db.moodDao())
        val settingsManager = SettingsManager(applicationContext)
        val settings = settingsManager.settingsFlow.first()
        val privacy = settingsManager.privacySettingsFlow.first()
        if (!settings.weeklyReportEnabled) return Result.success()
        val entries = repository.allEntries.first()
        val zoneId = ZoneId.systemDefault()
        val endDate = WeeklyReportScheduler.reportEndDateForExecution(
            ZonedDateTime.now(zoneId),
            settings
        )
        val notificationHelper = NotificationHelper(applicationContext)
        if (MonthlyReportScheduler.shouldReplaceWeeklyReport(endDate, settings)) {
            val monthlyReport = MonthlyReportAnalytics.build(entries, settings, endDate, zoneId)
                ?: return Result.success()
            notificationHelper.showMonthlyReport(monthlyReport, settings, privacy)
        } else {
            val weeklyReport = WeeklyReportAnalytics.build(entries, settings, endDate, zoneId)
                ?: return Result.success()
            notificationHelper.showWeeklyReport(weeklyReport, settings, privacy)
        }
        return Result.success()
    }
}
