package com.nickspeelman.localjournal.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.workers.MonthlySummaryWorker
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object MonthlyReportScheduler {
    private const val UNIQUE_WORK_NAME = "monthly_summary"

    suspend fun ensureScheduled(context: Context) {
        val settings = SettingsManager(context.applicationContext).settingsFlow.first()
        reschedule(context, settings)
    }

    fun reschedule(context: Context, settings: UserSettings) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(UNIQUE_WORK_NAME)
        if (shouldScheduleSeparate(settings)) {
            enqueueNext(context, settings, ExistingWorkPolicy.REPLACE)
        }
    }

    fun schedulingInputsChanged(old: UserSettings, new: UserSettings): Boolean {
        if (old.monthlyReportEnabled != new.monthlyReportEnabled) return true
        if (old.weeklyReportEnabled != new.weeklyReportEnabled) return true
        if (old.monthlyReportReplaceWeekly != new.monthlyReportReplaceWeekly) return true
        if (old.monthlyReportUseBedtimeOffset != new.monthlyReportUseBedtimeOffset) return true
        return if (new.monthlyReportUseBedtimeOffset) {
            old.sleepStartHour != new.sleepStartHour ||
                old.sleepStartMinute != new.sleepStartMinute ||
                old.sleepEndHour != new.sleepEndHour ||
                old.sleepEndMinute != new.sleepEndMinute
        } else {
            old.monthlyReportHour != new.monthlyReportHour ||
                old.monthlyReportMinute != new.monthlyReportMinute
        }
    }

    fun shouldScheduleSeparate(settings: UserSettings): Boolean =
        settings.monthlyReportEnabled && (!settings.weeklyReportEnabled || !settings.monthlyReportReplaceWeekly)

    /** Default replacement occurs on the last configured weekly-report day that falls in the month. */
    fun shouldReplaceWeeklyReport(reportEndDate: LocalDate, settings: UserSettings): Boolean =
        settings.monthlyReportEnabled &&
            settings.weeklyReportEnabled &&
            settings.monthlyReportReplaceWeekly &&
            reportEndDate.plusWeeks(1).month != reportEndDate.month

    fun calculateInitialDelayMillis(now: ZonedDateTime, settings: UserSettings): Long {
        var month = YearMonth.from(now)
        var endDate = month.atEndOfMonth()
        var target = deliveryTimeFor(endDate, settings, now)
        if (!target.isAfter(now)) {
            month = month.plusMonths(1)
            endDate = month.atEndOfMonth()
            target = deliveryTimeFor(endDate, settings, now)
        }
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }

    /** Resolve the calendar month-end represented by a delayed one-time worker execution. */
    fun reportEndDateForExecution(now: ZonedDateTime, settings: UserSettings): LocalDate {
        val thisMonthEnd = YearMonth.from(now).atEndOfMonth()
        val previousMonthEnd = YearMonth.from(now).minusMonths(1).atEndOfMonth()
        return listOf(previousMonthEnd, thisMonthEnd)
            .map { it to deliveryTimeFor(it, settings, now) }
            .filter { (_, target) -> !target.isAfter(now) }
            .maxByOrNull { (_, target) -> target }
            ?.first
            ?: previousMonthEnd
    }

    fun bedtimeOffsetClockTime(settings: UserSettings): LocalTime =
        LocalTime.of(settings.sleepStartHour, settings.sleepStartMinute).minusHours(2)

    private fun deliveryTimeFor(
        reportDate: LocalDate,
        settings: UserSettings,
        reference: ZonedDateTime
    ): ZonedDateTime {
        if (!settings.monthlyReportUseBedtimeOffset) {
            return ZonedDateTime.of(
                reportDate,
                LocalTime.of(
                    settings.monthlyReportHour.coerceIn(0, 23),
                    settings.monthlyReportMinute.coerceIn(0, 59)
                ),
                reference.zone
            )
        }

        val wake = LocalTime.of(settings.sleepEndHour, settings.sleepEndMinute)
        val bed = LocalTime.of(settings.sleepStartHour, settings.sleepStartMinute)
        val bedDate = if (!bed.isAfter(wake)) reportDate.plusDays(1) else reportDate
        return ZonedDateTime.of(bedDate, bed, reference.zone).minusHours(2)
    }

    fun scheduleNextAfterExecution(context: Context, settings: UserSettings) {
        if (shouldScheduleSeparate(settings)) {
            enqueueNext(context, settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
    }

    private fun enqueueNext(
        context: Context,
        settings: UserSettings,
        policy: ExistingWorkPolicy
    ) {
        val delay = calculateInitialDelayMillis(ZonedDateTime.now(), settings)
        val request = OneTimeWorkRequestBuilder<MonthlySummaryWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            request
        )
    }
}
