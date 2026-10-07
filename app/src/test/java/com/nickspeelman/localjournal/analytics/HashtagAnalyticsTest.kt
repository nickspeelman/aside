package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.MoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HashtagAnalyticsTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun snapshot_countsUnratedEntriesButExcludesThemFromAverage() {
        val entries = listOf(
            entry(null, "2026-10-01T10:00", "#Work"),
            entry(5, "2026-10-02T10:00", "#work"),
            entry(3, "2026-10-03T10:00", "#WORK,#work")
        )

        val stat = HashtagAnalytics.snapshot(entries, zoneId = zone).stats.single()

        assertEquals("#work", stat.tag)
        assertEquals(3, stat.entryCount)
        assertEquals(2, stat.ratedEntryCount)
        assertEquals(4.0, stat.averageRating!!, 0.001)
    }

    @Test
    fun snapshot_keepsTagWithOnlyUnratedEntries() {
        val stat = HashtagAnalytics.snapshot(
            listOf(entry(null, "2026-10-01T10:00", "#noteonly")),
            zoneId = zone
        ).stats.single()

        assertEquals(1, stat.entryCount)
        assertEquals(0, stat.ratedEntryCount)
        assertNull(stat.averageRating)
    }

    @Test
    fun averageSort_placesTagsWithoutRatingsLast() {
        val stats = HashtagAnalytics.snapshot(
            listOf(
                entry(null, "2026-10-01T10:00", "#none"),
                entry(2, "2026-10-02T10:00", "#low"),
                entry(5, "2026-10-03T10:00", "#high")
            ),
            zoneId = zone
        ).stats

        assertEquals(
            listOf("#high", "#low", "#none"),
            HashtagAnalytics.filterAndSort(stats, "", HashtagSort.HIGHEST_AVERAGE).map { it.tag }
        )
        assertEquals(
            listOf("#low", "#high", "#none"),
            HashtagAnalytics.filterAndSort(stats, "", HashtagSort.LOWEST_AVERAGE).map { it.tag }
        )
    }

    private fun entry(rating: Int?, time: String, hashtags: String) = MoodEntry(
        rating = rating,
        note = "",
        hashtags = hashtags,
        timestamp = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()
    )
}
