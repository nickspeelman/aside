package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MoodAnalyticsTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun homeSummary_usesCalendarWindowsAndHashtagAverages() {
        val today = LocalDate.of(2026, 9, 22)
        val entries = listOf(
            entry(4, "2026-09-22T09:00", "#Home,#HOME"),
            entry(2, "2026-09-21T10:00", "#work"),
            entry(5, "2026-09-15T11:00", "#home"),
            entry(1, "2026-08-20T12:00", "#home"),
            entry(3, "2026-08-15T12:00", "#work")
        )

        val summary = MoodAnalytics.homeSummary(entries, today, zone)

        assertEquals(4.0, summary.today.current!!, 0.001)
        assertEquals(2.0, summary.today.previous!!, 0.001)
        assertEquals(3.0, summary.last7Days.current!!, 0.001)
        assertEquals(5.0, summary.last7Days.previous!!, 0.001)
        assertEquals(MoodAnalytics.TrendDirection.UP, summary.today.direction)
        assertEquals(3, summary.topHashtags.first { it.tag == "#home" }.entryCount)
        assertEquals((4 + 5 + 1) / 3.0, summary.topHashtags.first { it.tag == "#home" }.averageRating!!, 0.001)
    }

    @Test
    fun trendDirection_comparesDisplayedTenths() {
        val metric = MoodAnalytics.ComparisonMetric(current = 3.84, previous = 3.81)
        assertEquals(MoodAnalytics.TrendDirection.FLAT, metric.direction)
    }

    @Test
    fun trackingWindow_runsFromWakeUpToBedtimeAcrossMidnight() {
        val settings = settings(wakeHour = 8, bedtimeHour = 2)
        val window = MoodAnalytics.trackingWindow(settings)

        assertEquals(18 * 60, window.durationMinutes)

        val date = LocalDate.of(2026, 9, 22)
        val entries = listOf(
            entry(4, "2026-09-22T08:30"),
            entry(5, "2026-09-22T23:30"),
            entry(3, "2026-09-22T01:00"),
            entry(1, "2026-09-22T04:00")
        )

        // With an 08:00 -> 02:00 tracking window, 01:00 is inside and 04:00 is outside.
        assertEquals(
            3,
            MoodAnalytics.entriesInTrackingWindowCount(entries, date, date, settings, zone)
        )
    }

    @Test
    fun weekdaySeries_leavesMissingWeekdaysNull() {
        val start = LocalDate.of(2026, 9, 21) // Monday
        val entries = listOf(
            entry(5, "2026-09-21T09:00"),
            entry(3, "2026-09-21T15:00"),
            entry(2, "2026-09-23T12:00")
        )

        val points = MoodAnalytics.weekdaySeries(entries, start, start.plusDays(6), zone)

        assertEquals(4.0, points[0].average!!, 0.001)
        assertNull(points[1].average)
        assertEquals(2.0, points[2].average!!, 0.001)
    }

    @Test
    fun dailySeries_preservesMissingDaysAndComputesTrailingTrend() {
        val start = LocalDate.of(2026, 9, 20)
        val entries = listOf(
            entry(5, "2026-09-20T09:00"),
            entry(3, "2026-09-22T09:00")
        )

        val points = MoodAnalytics.dailySeries(entries, start, start.plusDays(2), zone)

        assertEquals(5.0, points[0].average!!, 0.001)
        assertNull(points[1].average)
        assertEquals(3.0, points[2].average!!, 0.001)
        assertEquals(4.0, points[2].movingAverage7Day!!, 0.001)
    }

    @Test
    fun adaptiveTrend_usesThreeDaysForSevenDayViewAndBridgesSparseData() {
        val start = LocalDate.of(2026, 9, 20)
        val entries = listOf(entry(5, "2026-09-23T09:00"))
        val points = MoodAnalytics.dailySeries(entries, start, start.plusDays(6), zone)

        val window = MoodAnalytics.adaptiveTrendWindowDays(points.size)
        val trend = MoodAnalytics.dailyTrendValues(points, window)

        assertEquals(3, window)
        assertNull(trend[1])
        assertEquals(5.0, trend[2]!!, 0.001)
        assertEquals(5.0, trend[3]!!, 0.001)
        assertEquals(5.0, trend[4]!!, 0.001)
        assertNull(trend[5])
    }

    @Test
    fun adaptiveTrendWindow_growsWithLongerRanges() {
        assertEquals(3, MoodAnalytics.adaptiveTrendWindowDays(7))
        assertEquals(7, MoodAnalytics.adaptiveTrendWindowDays(30))
        assertEquals(14, MoodAnalytics.adaptiveTrendWindowDays(90))
        assertEquals(30, MoodAnalytics.adaptiveTrendWindowDays(365))
    }

    @Test
    fun timeOfDaySeries_doesNotInventValuesFarFromObservations() {
        val settings = settings(wakeHour = 8, bedtimeHour = 22)
        val date = LocalDate.of(2026, 9, 22)
        val entries = listOf(entry(5, "2026-09-22T08:15"))

        val series = MoodAnalytics.timeOfDaySeries(
            entries,
            date,
            date,
            settings,
            zone,
            sampleCount = 15
        )

        assertTrue(series.take(4).any { it.average != null })
        assertTrue(series.takeLast(4).all { it.average == null })
    }


    @Test
    fun `unrated entries are excluded from mood calculations`() {
        val zone = ZoneId.of("UTC")
        val entries = listOf(
            MoodEntry(
                rating = null,
                note = "note only",
                hashtags = "#work",
                timestamp = LocalDateTime.parse("2026-09-23T12:00:00")
                    .atZone(zone).toInstant().toEpochMilli()
            ),
            MoodEntry(
                rating = 5,
                note = "",
                hashtags = "#work",
                timestamp = LocalDateTime.parse("2026-09-23T13:00:00")
                    .atZone(zone).toInstant().toEpochMilli()
            )
        )

        val summary = MoodAnalytics.homeSummary(
            entries = entries,
            today = LocalDate.of(2026, 9, 23),
            zoneId = zone
        )

        assertEquals(5.0, summary.today.current!!, 0.0001)
        assertEquals(2, summary.topHashtags.single().entryCount)
    }

    private fun entry(rating: Int, localDateTime: String, hashtags: String = ""): MoodEntry {
        val millis = LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli()
        return MoodEntry(rating = rating, note = "", hashtags = hashtags, timestamp = millis)
    }

    private fun settings(wakeHour: Int, bedtimeHour: Int): UserSettings = UserSettings(
        promptsPerDay = 3,
        sleepStartHour = bedtimeHour,
        sleepStartMinute = 0,
        sleepEndHour = wakeHour,
        sleepEndMinute = 0,
        isPaused = false,
        use24Hour = false
    )
}
