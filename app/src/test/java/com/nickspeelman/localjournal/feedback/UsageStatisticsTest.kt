package com.nickspeelman.localjournal.feedback

import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.data.ManualBackupState
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.privacy.PrivacyPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class UsageStatisticsTest {
    @Test
    fun snapshot_countsActivityWithoutReadingJournalContent() {
        val now = LocalDate.of(2026, 11, 3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val entries = listOf(
            MoodEntry(1, 4, "private words", "#private", now),
            MoodEntry(2, null, "", "", now - 8L * 24 * 60 * 60 * 1000)
        )

        val snapshot = UsageStatistics.snapshot(entries, now, ZoneOffset.UTC)

        assertEquals(2, snapshot.totalEntries)
        assertEquals(1, snapshot.ratedEntries)
        assertEquals(1, snapshot.noteOnlyEntries)
        assertEquals(1, snapshot.entriesWithNotes)
        assertEquals(1, snapshot.entriesWithHashtags)
        assertEquals(2, snapshot.activeDays)
        assertEquals(1, snapshot.entriesLast7Days)
        assertEquals(2, snapshot.entriesLast30Days)
    }

    @Test
    fun body_containsAggregatesButNeverJournalContent() {
        val entry = MoodEntry(
            id = 1,
            rating = 2,
            note = "private words",
            hashtags = "#private",
            timestamp = 1_700_000_000_000
        )
        val body = UsageStatistics.body(
            platform = listOf("Aside version" to "test"),
            snapshot = UsageStatistics.snapshot(listOf(entry), nowMillis = entry.timestamp, zoneId = ZoneOffset.UTC),
            settings = UserSettings(),
            privacy = PrivacyPresets.balanced,
            backupPolicy = AndroidBackupPolicy.BALANCED,
            backupState = ManualBackupState()
        )

        assertTrue(body.contains("Total entries: 1"))
        assertTrue(body.contains("No journal text, hashtag values, mood values"))
        assertFalse(body.contains("private words"))
        assertFalse(body.contains("#private"))
    }
}
