package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MonthlyReportAnalyticsTest {
    private val zone = ZoneId.of("America/New_York")
    private val settings = UserSettings(
        promptsPerDay = 3,
        sleepStartHour = 22,
        sleepStartMinute = 0,
        sleepEndHour = 8,
        sleepEndMinute = 0
    )

    @Test
    fun build_usesThirtyDaysAndComparesPriorThirty() {
        val end = LocalDate.of(2026, 9, 30)
        val entries = listOf(
            entry(5, "2026-09-30T09:00"),
            entry(3, "2026-09-15T09:00"),
            entry(2, "2026-08-31T09:00"),
            entry(4, "2026-08-15T09:00")
        )
        val report = MonthlyReportAnalytics.build(entries, settings, end, zone)!!
        assertEquals(LocalDate.of(2026, 9, 1), report.startDate)
        assertEquals(4.0, report.average, 0.001)
        assertEquals(3.0, report.previousAverage!!, 0.001)
        assertEquals(30, report.currentDays.size)
        assertEquals(30, report.previousDays.size)
    }


    @Test
    fun build_returnsNullWhenMonthContainsOnlyUnratedNotes() {
        val report = MonthlyReportAnalytics.build(
            entries = listOf(entry(null, "2026-09-15T09:00")),
            settings = settings,
            endDate = LocalDate.of(2026, 9, 30),
            zoneId = zone
        )
        assertNull(report)
    }

    @Test
    fun monthlyTrendUsesCenteredSevenDayWindow() {
        val start = LocalDate.of(2026, 9, 1)
        val days = (0 until 30).map { index ->
            MonthlyReportAnalytics.DayValue(
                date = start.plusDays(index.toLong()),
                average = if (index == 10) 4.0 else null,
                count = if (index == 10) 2 else 0
            )
        }
        val trend = MonthlyReportAnalytics.trendValues(days)
        assertEquals(4.0, trend[7]!!, 0.001)
        assertEquals(4.0, trend[10]!!, 0.001)
        assertEquals(4.0, trend[13]!!, 0.001)
        assertNull(trend[6])
        assertNull(trend[14])
    }

    private fun entry(rating: Int?, dateTime: String): MoodEntry = MoodEntry(
        rating = rating,
        note = "",
        hashtags = "",
        timestamp = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()
    )
}
