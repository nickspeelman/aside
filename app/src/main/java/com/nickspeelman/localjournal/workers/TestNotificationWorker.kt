package com.nickspeelman.localjournal.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nickspeelman.localjournal.analytics.MonthlyReportAnalytics
import com.nickspeelman.localjournal.analytics.WeeklyReportAnalytics
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.notifications.NotificationHelper
import com.nickspeelman.localjournal.notifications.TestNotificationType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Posts a deliberately delayed debug notification so lock-screen privacy can be tested.
 * This worker is only enqueued from Settings; it is not part of Aside's normal scheduling.
 */
class TestNotificationWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val typeName = inputData.getString(KEY_TEST_TYPE) ?: return Result.failure()
        val type = runCatching { TestNotificationType.valueOf(typeName) }
            .getOrElse { return Result.failure() }

        val settingsManager = SettingsManager(applicationContext)
        val settings = settingsManager.settingsFlow.first()
        val privacy = settingsManager.privacySettingsFlow.first()
        val notificationHelper = NotificationHelper(applicationContext)

        when (type) {
            TestNotificationType.CHECK_IN -> {
                notificationHelper.showMoodPrompt(privacy, settings.checkInNotificationTimeoutMinutes)
            }

            TestNotificationType.WEEKLY_REPORT -> {
                val entries = MoodRepository(
                    MoodDatabase.getDatabase(applicationContext).moodDao()
                ).snapshot()
                val zoneId = ZoneId.systemDefault()
                val report = WeeklyReportAnalytics.build(
                    entries = entries,
                    settings = settings,
                    endDate = LocalDate.now(zoneId),
                    zoneId = zoneId
                ) ?: return Result.success()
                notificationHelper.showWeeklyReport(report, settings, privacy)
            }

            TestNotificationType.MONTHLY_REPORT -> {
                val entries = MoodRepository(
                    MoodDatabase.getDatabase(applicationContext).moodDao()
                ).snapshot()
                val zoneId = ZoneId.systemDefault()
                val report = MonthlyReportAnalytics.build(
                    entries = entries,
                    settings = settings,
                    endDate = LocalDate.now(zoneId),
                    zoneId = zoneId
                ) ?: return Result.success()
                notificationHelper.showMonthlyReport(report, settings, privacy)
            }
        }

        return Result.success()
    }

    companion object {
        const val KEY_TEST_TYPE = "test_notification_type"
    }
}
