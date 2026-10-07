package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RangeReportAnalyticsTest {
    private val zone = ZoneId.of("America/New_York")
    private val settings = UserSettings(
        sleepStartHour = 22,
        sleepEndHour = 8
    )

    @Test
    fun selectedRange_matchesNotificationStyleSummaryFields() {
        val entries = listOf(
            entry(2, "2026-09-19T09:00", "#home"),
            entry(4, "2026-09-20T10:00", "#home"),
            entry(5, "2026-09-24T19:00", "#friends"),
            entry(3, "2026-09-12T09:00"),
            entry(3, "2026-09-13T09:00")
        )

        val report = RangeReportAnalytics.build(
            entries = entries,
            settings = settings,
            startDate = LocalDate.of(2026, 9, 19),
            endDate = LocalDate.of(2026, 9, 25),
            zoneId = zone
        )!!

        assertEquals(3, report.checkInCount)
        assertEquals((2 + 4 + 5) / 3.0, report.average, 0.001)
        assertEquals(3.0, report.previousAverage!!, 0.001)
        assertEquals(LocalDate.of(2026, 9, 24), report.highest!!.date)
        assertEquals(LocalDate.of(2026, 9, 19), report.lowest!!.date)
        assertEquals("#home", report.hashtags.single().tag)
        assertEquals(2, report.hashtags.single().count)
    }

    @Test
    fun meaningfulTimeOfDayDifferences_areIncluded() {
        val entries = listOf(
            entry(2, "2026-09-19T09:00"),
            entry(2, "2026-09-20T10:00"),
            entry(5, "2026-09-23T19:00"),
            entry(5, "2026-09-24T20:00")
        )

        val report = RangeReportAnalytics.build(
            entries,
            settings,
            LocalDate.of(2026, 9, 19),
            LocalDate.of(2026, 9, 25),
            zone
        )!!

        assertTrue(report.timeBuckets.size >= 2)
    }

    private fun entry(rating: Int, localDateTime: String, hashtags: String = ""): MoodEntry {
        val millis = LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli()
        return MoodEntry(rating = rating, note = "", hashtags = hashtags, timestamp = millis)
    }
}
