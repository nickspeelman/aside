package com.nickspeelman.localjournal

import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.notifications.WeeklyReportScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklyReportSchedulerTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun normalBedtimeSchedulesSundayTwoHoursBeforeBed() {
        val settings = settings(wake = 8, bed = 22)
        val now = ZonedDateTime.of(2026, 9, 22, 12, 0, 0, 0, zone) // Tuesday
        val delay = WeeklyReportScheduler.calculateInitialDelayMillis(now, settings)
        val target = now.plus(Duration.ofMillis(delay))

        assertEquals(2026, target.year)
        assertEquals(9, target.monthValue)
        assertEquals(27, target.dayOfMonth)
        assertEquals(20, target.hour)
    }

    @Test
    fun afterMidnightBedtimeBelongsToEndOfSundayTrackingDay() {
        val settings = settings(wake = 8, bed = 2)
        val now = ZonedDateTime.of(2026, 9, 22, 12, 0, 0, 0, zone)
        val delay = WeeklyReportScheduler.calculateInitialDelayMillis(now, settings)
        val target = now.plus(Duration.ofMillis(delay))

        // Monday 02:00 bedtime minus two hours = Monday midnight.
        assertEquals(28, target.dayOfMonth)
        assertEquals(0, target.hour)
    }

    @Test
    fun customDayAndTimeAreUsedExactly() {
        val settings = settings(wake = 8, bed = 22).copy(
            weeklyReportDay = 3, // Wednesday
            weeklyReportHour = 18,
            weeklyReportMinute = 30,
            weeklyReportUseBedtimeOffset = false
        )
        val now = ZonedDateTime.of(2026, 9, 22, 12, 0, 0, 0, zone) // Tuesday
        val target = now.plus(Duration.ofMillis(
            WeeklyReportScheduler.calculateInitialDelayMillis(now, settings)
        ))

        assertEquals(23, target.dayOfMonth)
        assertEquals(18, target.hour)
        assertEquals(30, target.minute)
    }

    @Test
    fun workerMapsLateDeliveryBackToConfiguredReportDay() {
        val settings = settings(wake = 8, bed = 22).copy(
            weeklyReportDay = 5, // Friday
            weeklyReportHour = 20,
            weeklyReportMinute = 0,
            weeklyReportUseBedtimeOffset = false
        )
        val lateSaturday = ZonedDateTime.of(2026, 9, 26, 2, 0, 0, 0, zone)

        assertEquals(
            java.time.LocalDate.of(2026, 9, 25),
            WeeklyReportScheduler.reportEndDateForExecution(lateSaturday, settings)
        )
    }

    @Test
    fun afterMidnightDefaultWorkerStillReportsSelectedSunday() {
        val settings = settings(wake = 8, bed = 2)
        val mondayJustAfterDelivery = ZonedDateTime.of(2026, 9, 28, 0, 5, 0, 0, zone)

        assertEquals(
            java.time.LocalDate.of(2026, 9, 27),
            WeeklyReportScheduler.reportEndDateForExecution(mondayJustAfterDelivery, settings)
        )
    }

    private fun settings(wake: Int, bed: Int) = UserSettings(
        promptsPerDay = 3,
        sleepStartHour = bed,
        sleepStartMinute = 0,
        sleepEndHour = wake,
        sleepEndMinute = 0
    )
}
