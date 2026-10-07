package com.nickspeelman.localjournal

import com.nickspeelman.localjournal.data.JournalCsvExporter
import com.nickspeelman.localjournal.data.MoodEntry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class JournalCsvExporterTest {
    @Test
    fun `csv escapes commas quotes newlines unicode and nullable rating`() {
        val csv = JournalCsvExporter.encode(
            listOf(
                MoodEntry(
                    id = 1,
                    rating = null,
                    note = "comma, quote \" and\nnewline 🙂",
                    hashtags = "#one,#two",
                    timestamp = 0L
                )
            )
        )

        assertTrue(csv.startsWith("timestamp,mood_rating,note,hashtags\n"))
        assertTrue(csv.contains("1970-01-01T00:00:00Z,,"))
        assertTrue(csv.contains("\"comma, quote \"\" and\nnewline 🙂\""))
        assertTrue(csv.contains("\"#one,#two\""))
    }

    @Test
    fun `csv neutralizes spreadsheet formula prefixes`() {
        val csv = JournalCsvExporter.encode(
            listOf(
                MoodEntry(
                    id = 1,
                    rating = 4,
                    note = "=HYPERLINK(\"https://example.test\")",
                    hashtags = "@tag",
                    timestamp = 0L
                )
            )
        )

        assertTrue(csv.contains("'="))
        assertTrue(csv.contains(",'@tag"))
        assertFalse(csv.contains(",=HYPERLINK"))
    }
}
