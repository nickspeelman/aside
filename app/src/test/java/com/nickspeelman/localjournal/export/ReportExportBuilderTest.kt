package com.nickspeelman.localjournal.export

import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportExportBuilderTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun `exported hashtag labels retain hash prefix`() {
        val entries = listOf(
            entry(4, "2026-10-01", "Good #work day", "#work"),
            entry(3, "2026-10-02", "Another #work day", "#work")
        )
        val report = ReportExportBuilder.build(
            kind = ReportExportKind.RANGE,
            entries = entries,
            settings = UserSettings(),
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-02"),
            includeHashtags = true,
            includeNotes = false,
            zoneId = zone
        )!!

        assertEquals("#work", report.hashtags.single().tag)
    }

    @Test
    fun `note export preserves inline hashtags and restores missing legacy hashtag tokens`() {
        val entries = listOf(
            entry(4, "2026-10-01", "Fun day with #michele at home", "#michele,#home"),
            entry(3, "2026-10-02", "Another entry", "")
        )
        val report = ReportExportBuilder.build(
            kind = ReportExportKind.RANGE,
            entries = entries,
            settings = UserSettings(),
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-02"),
            includeHashtags = false,
            includeNotes = true,
            zoneId = zone
        )!!

        assertEquals("Fun day with #michele at home #home", report.notes.first().note)
        assertTrue(report.notes.first().note.contains("#michele"))
        assertTrue(report.notes.first().note.contains("#home"))
    }

    private fun entry(rating: Int, date: String, note: String, hashtags: String): MoodEntry =
        MoodEntry(
            rating = rating,
            note = note,
            hashtags = hashtags,
            timestamp = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        )
}
