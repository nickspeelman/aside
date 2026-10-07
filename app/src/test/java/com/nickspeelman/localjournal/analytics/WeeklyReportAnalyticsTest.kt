package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklyReportAnalyticsTest {
    private val zone = ZoneId.of("America/New_York")
    private val settings = UserSettings(
        promptsPerDay = 3,
        sleepStartHour = 22,
        sleepStartMinute = 0,
        sleepEndHour = 8,
        sleepEndMinute = 0
    )

    @Test
    fun build_comparesTwoWeeksAndFindsDailyExtremes() {
        val end = LocalDate.of(2026, 9, 20) // Sunday
        val entries = listOf(
            entry(2, "2026-09-14T09:00"),
            entry(4, "2026-09-15T09:00"),
            entry(5, "2026-09-19T09:00"),
            entry(3, "2026-09-07T09:00"),
            entry(3, "2026-09-08T09:00")
        )

        val report = WeeklyReportAnalytics.build(entries, settings, end, zone)!!

        assertEquals((2 + 4 + 5) / 3.0, report.average, 0.001)
        assertEquals(3.0, report.previousAverage!!, 0.001)
        assertEquals(DayOfWeek.SATURDAY, report.highest!!.day)
        assertEquals(DayOfWeek.MONDAY, report.lowest!!.day)
        assertEquals(7, report.currentDays.size)
        assertEquals(7, report.previousDays.size)
    }

    @Test
    fun hashtags_requireTwoOccurrencesAndCountOncePerEntry() {
        val end = LocalDate.of(2026, 9, 20)
        val entries = listOf(
            entry(5, "2026-09-14T09:00", "#Home,#HOME"),
            entry(3, "2026-09-15T09:00", "#home"),
            entry(2, "2026-09-16T09:00", "#work")
        )

        val report = WeeklyReportAnalytics.build(entries, settings, end, zone)!!

        assertEquals(1, report.hashtags.size)
        assertEquals("#home", report.hashtags.single().tag)
        assertEquals(2, report.hashtags.single().count)
        assertEquals(4.0, report.hashtags.single().average, 0.001)
    }

    @Test
    fun timeOfDayAppearsOnlyForMeaningfulSpread() {
        val end = LocalDate.of(2026, 9, 20)
        val entries = listOf(
            entry(2, "2026-09-14T09:00"),
            entry(2, "2026-09-15T10:00"),
            entry(5, "2026-09-16T19:00"),
            entry(5, "2026-09-17T20:00")
        )

        val report = WeeklyReportAnalytics.build(entries, settings, end, zone)!!
        assertTrue(report.timeBuckets.size >= 2)
    }

    @Test
    fun weeklyTrend_usesCenteredThreeDayWindowForSparseWeek() {
        val days = listOf(
            WeeklyReportAnalytics.DayValue(DayOfWeek.MONDAY, null, 0),
            WeeklyReportAnalytics.DayValue(DayOfWeek.TUESDAY, null, 0),
            WeeklyReportAnalytics.DayValue(DayOfWeek.WEDNESDAY, null, 0),
            WeeklyReportAnalytics.DayValue(DayOfWeek.THURSDAY, 4.0, 2),
            WeeklyReportAnalytics.DayValue(DayOfWeek.FRIDAY, null, 0),
            WeeklyReportAnalytics.DayValue(DayOfWeek.SATURDAY, null, 0),
            WeeklyReportAnalytics.DayValue(DayOfWeek.SUNDAY, null, 0)
        )

        val trend = WeeklyReportAnalytics.trendValues(days)

        assertNull(trend[1])
        assertEquals(4.0, trend[2]!!, 0.001)
        assertEquals(4.0, trend[3]!!, 0.001)
        assertEquals(4.0, trend[4]!!, 0.001)
        assertNull(trend[5])
    }

    @Test
    fun build_returnsNullWhenWeekHasNoEntries() {
        val report = WeeklyReportAnalytics.build(
            entries = listOf(entry(5, "2026-09-01T09:00")),
            settings = settings,
            endDate = LocalDate.of(2026, 9, 20),
            zoneId = zone
        )
        assertNull(report)
    }


    @Test
    fun build_returnsNullWhenWeekContainsOnlyUnratedNotes() {
        val report = WeeklyReportAnalytics.build(
            entries = listOf(entry(null, "2026-09-18T09:00")),
            settings = settings,
            endDate = LocalDate.of(2026, 9, 20),
            zoneId = zone
        )
        assertNull(report)
    }

    @Test
    fun mostRecentSundayDoesNotShiftLateMondayDeliveryIntoNewWeek() {
        assertEquals(
            LocalDate.of(2026, 9, 20),
            WeeklyReportAnalytics.mostRecentSunday(LocalDate.of(2026, 9, 21))
        )
    }

    private fun entry(rating: Int?, dateTime: String, hashtags: String = ""): MoodEntry {
        return MoodEntry(
            rating = rating,
            note = "",
            hashtags = hashtags,
            timestamp = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()
        )
    }
}
