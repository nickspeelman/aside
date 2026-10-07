package com.nickspeelman.localjournal.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.workers.WeeklySummaryWorker
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

object WeeklyReportScheduler {
    private const val UNIQUE_WORK_NAME = "weekly_summary"

    suspend fun ensureScheduled(context: Context) {
        val settings = SettingsManager(context.applicationContext).settingsFlow.first()
        if (!settings.weeklyReportEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        enqueue(context, settings, ExistingPeriodicWorkPolicy.KEEP)
    }

    fun reschedule(context: Context, settings: UserSettings) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(UNIQUE_WORK_NAME)
        if (settings.weeklyReportEnabled) {
            enqueue(context, settings, ExistingPeriodicWorkPolicy.REPLACE)
        }
    }

    fun schedulingInputsChanged(old: UserSettings, new: UserSettings): Boolean {
        if (old.weeklyReportEnabled != new.weeklyReportEnabled) return true
        if (old.weeklyReportDay != new.weeklyReportDay) return true
        if (old.weeklyReportUseBedtimeOffset != new.weeklyReportUseBedtimeOffset) return true
        return if (new.weeklyReportUseBedtimeOffset) {
            old.sleepStartHour != new.sleepStartHour ||
                old.sleepStartMinute != new.sleepStartMinute ||
                old.sleepEndHour != new.sleepEndHour ||
                old.sleepEndMinute != new.sleepEndMinute
        } else {
            old.weeklyReportHour != new.weeklyReportHour ||
                old.weeklyReportMinute != new.weeklyReportMinute
        }
    }

    /**
     * Returns the next configured weekly-report delivery time.
     *
     * The default mode treats the selected weekday as the waking/tracking day and delivers two
     * hours before its bedtime. For schedules whose bedtime is after midnight, that can naturally
     * land on the following calendar date. Once the user selects an exact time, the report is sent
     * on the selected weekday at that clock time instead.
     */
    fun calculateInitialDelayMillis(
        now: ZonedDateTime,
        settings: UserSettings
    ): Long {
        val reportDay = configuredDay(settings)
        var reportDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(reportDay))
        var target = deliveryTimeFor(reportDate, settings, now)
        if (!target.isAfter(now)) {
            reportDate = reportDate.plusWeeks(1)
            target = deliveryTimeFor(reportDate, settings, now)
        }
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }

    /**
     * Finds the report-period end date represented by a worker run, including delayed WorkManager
     * delivery and the default after-midnight bedtime case.
     */
    fun reportEndDateForExecution(
        now: ZonedDateTime,
        settings: UserSettings
    ): LocalDate {
        val reportDay = configuredDay(settings)
        var reportDate = now.toLocalDate().with(TemporalAdjusters.previousOrSame(reportDay))
        if (deliveryTimeFor(reportDate, settings, now).isAfter(now)) {
            reportDate = reportDate.minusWeeks(1)
        }
        return reportDate
    }

    /** Exact clock time shown in Settings for the default relative schedule. */
    fun bedtimeOffsetClockTime(settings: UserSettings): LocalTime =
        LocalTime.of(settings.sleepStartHour, settings.sleepStartMinute).minusHours(2)

    private fun configuredDay(settings: UserSettings): DayOfWeek =
        DayOfWeek.of(settings.weeklyReportDay.coerceIn(1, 7))

    private fun deliveryTimeFor(
        reportDate: LocalDate,
        settings: UserSettings,
        reference: ZonedDateTime
    ): ZonedDateTime {
        if (!settings.weeklyReportUseBedtimeOffset) {
            return ZonedDateTime.of(
                reportDate,
                LocalTime.of(
                    settings.weeklyReportHour.coerceIn(0, 23),
                    settings.weeklyReportMinute.coerceIn(0, 59)
                ),
                reference.zone
            )
        }

        val wake = LocalTime.of(settings.sleepEndHour, settings.sleepEndMinute)
        val bed = LocalTime.of(settings.sleepStartHour, settings.sleepStartMinute)
        val bedDate = if (!bed.isAfter(wake)) reportDate.plusDays(1) else reportDate
        return ZonedDateTime.of(bedDate, bed, reference.zone).minusHours(2)
    }

    private fun enqueue(
        context: Context,
        settings: UserSettings,
        policy: ExistingPeriodicWorkPolicy
    ) {
        val delay = calculateInitialDelayMillis(ZonedDateTime.now(), settings)
        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request
        )
    }
}
