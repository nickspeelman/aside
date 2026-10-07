package com.nickspeelman.localjournal

import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.notifications.MonthlyReportScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MonthlyReportSchedulerTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun separateDefaultSchedulesLastDayTwoHoursBeforeBedtime() {
        val settings = settings().copy(
            monthlyReportReplaceWeekly = false,
            monthlyReportUseBedtimeOffset = true
        )
        val now = ZonedDateTime.of(2026, 9, 23, 12, 0, 0, 0, zone)
        val target = now.plus(Duration.ofMillis(
            MonthlyReportScheduler.calculateInitialDelayMillis(now, settings)
        ))
        assertEquals(30, target.dayOfMonth)
        assertEquals(20, target.hour)
    }

    @Test
    fun customMonthlyTimeIsUsedExactly() {
        val settings = settings().copy(
            monthlyReportReplaceWeekly = false,
            monthlyReportUseBedtimeOffset = false,
            monthlyReportHour = 18,
            monthlyReportMinute = 30
        )
        val now = ZonedDateTime.of(2026, 9, 23, 12, 0, 0, 0, zone)
        val target = now.plus(Duration.ofMillis(
            MonthlyReportScheduler.calculateInitialDelayMillis(now, settings)
        ))
        assertEquals(30, target.dayOfMonth)
        assertEquals(18, target.hour)
        assertEquals(30, target.minute)
    }

    @Test
    fun afterMidnightBedtimeCanDeliverOnFirstOfNextMonthButReportsPriorMonthEnd() {
        val settings = settings(wake = 8, bed = 2).copy(monthlyReportReplaceWeekly = false)
        val now = ZonedDateTime.of(2026, 9, 23, 12, 0, 0, 0, zone)
        val target = now.plus(Duration.ofMillis(
            MonthlyReportScheduler.calculateInitialDelayMillis(now, settings)
        ))
        assertEquals(10, target.monthValue)
        assertEquals(1, target.dayOfMonth)
        assertEquals(0, target.hour)

        val execution = ZonedDateTime.of(2026, 10, 1, 0, 5, 0, 0, zone)
        assertEquals(
            LocalDate.of(2026, 9, 30),
            MonthlyReportScheduler.reportEndDateForExecution(execution, settings)
        )
    }

    @Test
    fun lastWeeklyReportOfMonthIsDetected() {
        val settings = settings().copy(
            weeklyReportEnabled = true,
            monthlyReportEnabled = true,
            monthlyReportReplaceWeekly = true
        )
        assertTrue(
            MonthlyReportScheduler.shouldReplaceWeeklyReport(LocalDate.of(2026, 9, 27), settings)
        )
        assertFalse(
            MonthlyReportScheduler.shouldReplaceWeeklyReport(LocalDate.of(2026, 9, 20), settings)
        )
    }

    @Test
    fun monthlySchedulesSeparatelyWhenWeeklyIsOff() {
        val settings = settings().copy(
            weeklyReportEnabled = false,
            monthlyReportEnabled = true,
            monthlyReportReplaceWeekly = true
        )
        assertTrue(MonthlyReportScheduler.shouldScheduleSeparate(settings))
    }

    private fun settings(wake: Int = 8, bed: Int = 22) = UserSettings(
        promptsPerDay = 3,
        sleepStartHour = bed,
        sleepStartMinute = 0,
        sleepEndHour = wake,
        sleepEndMinute = 0
    )
}
