package com.nickspeelman.localjournal.export

import com.nickspeelman.localjournal.analytics.MoodAnalytics
import com.nickspeelman.localjournal.analytics.RangeReportAnalytics
import com.nickspeelman.localjournal.data.HashtagUtils
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportExportBuilder {
    fun build(
        kind: ReportExportKind,
        entries: List<MoodEntry>,
        settings: UserSettings,
        startDate: LocalDate,
        endDate: LocalDate,
        title: String? = null,
        includeHashtags: Boolean,
        includeNotes: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ShareableReport? {
        val summary = RangeReportAnalytics.build(entries, settings, startDate, endDate, zoneId) ?: return null
        val series = MoodAnalytics.dailySeries(entries, startDate, endDate, zoneId)
        val days = (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt()
        val trendWindow = when (kind) {
            ReportExportKind.WEEKLY -> 3
            ReportExportKind.MONTHLY -> 7
            ReportExportKind.RANGE -> MoodAnalytics.adaptiveTrendWindowDays(days)
        }
        val trend = MoodAnalytics.dailyTrendValues(series, trendWindow)
        val previousSeries = MoodAnalytics.dailySeries(entries, startDate.minusDays(days.toLong()), endDate.minusDays(days.toLong()), zoneId)
        val previousTrend = if (previousSeries.any { it.average != null }) MoodAnalytics.dailyTrendValues(previousSeries, trendWindow) else null
        val timeFormat = if (settings.use24Hour) DateTimeFormatter.ofPattern("HH:mm") else DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val notes = if (includeNotes) {
            entries.asSequence().filter { it.note.isNotBlank() }.map { it to Instant.ofEpochMilli(it.timestamp).atZone(zoneId) }
                .filter { (_, dt) -> !dt.toLocalDate().isBefore(startDate) && !dt.toLocalDate().isAfter(endDate) }
                .sortedBy { it.second.toInstant() }
                .map { (e, dt) -> ExportNote(dt.toLocalDateTime(), e.rating, noteDisplayContent(e)) }.toList()
        } else emptyList()
        return ShareableReport(
            kind = kind,
            title = title ?: when (kind) {
                ReportExportKind.WEEKLY -> "Weekly report"
                ReportExportKind.MONTHLY -> "Monthly report"
                ReportExportKind.RANGE -> "Mood report"
            },
            startDate = startDate,
            endDate = endDate,
            average = summary.average,
            previousAverage = summary.previousAverage,
            displayedDelta = summary.displayedDelta,
            checkInCount = summary.checkInCount,
            dailyValues = series.map { it.average },
            trendValues = trend,
            comparisonTrendValues = previousTrend,
            highest = summary.highest?.let { ExportHighlight(it.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())), it.average) },
            lowest = summary.lowest?.let { ExportHighlight(it.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())), it.average) },
            hashtags = if (includeHashtags) summary.hashtags.map { ExportHashtag(HashtagUtils.normalize(it.tag), it.average, it.count) } else emptyList(),
            timeBuckets = summary.timeBuckets.map { ExportTimeBucket("${it.start.format(timeFormat)}–${it.end.format(timeFormat)}", it.average, it.count) },
            notes = notes
        )
    }

    /**
     * Match History's user-facing treatment of hashtags. New entries preserve hashtags inline in
     * the note. Older entries may have had hashtags stored only in the separate index column, so
     * append only those missing indexed tags. Their original positions cannot be reconstructed.
     */
    private fun noteDisplayContent(entry: MoodEntry): String {
        val note = entry.note.trim()
        val tagsAlreadyInNote = HashtagUtils.extractFromContent(note).toSet()
        val missingTags = HashtagUtils.parse(entry.hashtags).filterNot { it in tagsAlreadyInNote }
        return listOf(note, missingTags.joinToString(" "))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    fun noteCount(entries: List<MoodEntry>, startDate: LocalDate, endDate: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Int =
        entries.count { e ->
            if (e.note.isBlank()) false else {
                val d = Instant.ofEpochMilli(e.timestamp).atZone(zoneId).toLocalDate()
                !d.isBefore(startDate) && !d.isAfter(endDate)
            }
        }
}
