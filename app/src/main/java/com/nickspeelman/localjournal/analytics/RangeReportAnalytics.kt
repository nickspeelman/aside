package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.round

/**
 * Summary metrics for the currently selected in-app report range.
 *
 * The calculations intentionally mirror the weekly/monthly notification reports so opening the
 * app does not lose context that was useful in the notification. Unlike those scheduled reports,
 * this accepts any date range and compares it with the immediately preceding equal-length period.
 */
object RangeReportAnalytics {

    data class DayHighlight(val date: LocalDate, val average: Double)
    data class HashtagValue(val tag: String, val average: Double, val count: Int)
    data class TimeBucket(
        val start: LocalTime,
        val end: LocalTime,
        val average: Double,
        val count: Int
    )

    data class Report(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val average: Double,
        val previousAverage: Double?,
        val checkInCount: Int,
        val highest: DayHighlight?,
        val lowest: DayHighlight?,
        val hashtags: List<HashtagValue>,
        val timeBuckets: List<TimeBucket>
    ) {
        val displayedDelta: Double?
            get() = previousAverage?.let {
                roundToTenth(average) - roundToTenth(it)
            }?.let(::roundToTenth)
    }

    fun build(
        entries: List<MoodEntry>,
        settings: UserSettings,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Report? {
        if (endDate.isBefore(startDate)) return null

        val currentEntries = entries.filter { entry ->
            entry.rating != null && entry.localDate(zoneId) in startDate..endDate
        }
        if (currentEntries.isEmpty()) return null

        val periodDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1).coerceAtLeast(1)
        val previousEnd = startDate.minusDays(1)
        val previousStart = previousEnd.minusDays(periodDays - 1)
        val previousEntries = entries.filter { entry ->
            entry.rating != null && entry.localDate(zoneId) in previousStart..previousEnd
        }

        val byDate = currentEntries.groupBy { it.localDate(zoneId) }
            .mapValues { (_, dayEntries) -> dayEntries.mapNotNull { it.rating }.average() }
        val highest = if (byDate.size >= 2) {
            byDate.maxByOrNull { it.value }?.let { DayHighlight(it.key, it.value) }
        } else null
        val lowest = if (byDate.size >= 2) {
            byDate.minByOrNull { it.value }?.let { DayHighlight(it.key, it.value) }
        } else null

        return Report(
            startDate = startDate,
            endDate = endDate,
            average = currentEntries.mapNotNull { it.rating }.average(),
            previousAverage = previousEntries.mapNotNull { it.rating }.averageOrNull(),
            checkInCount = currentEntries.size,
            highest = highest,
            lowest = lowest,
            hashtags = topHashtags(currentEntries),
            timeBuckets = interestingTimeBuckets(currentEntries, settings, zoneId)
        )
    }

    private fun topHashtags(entries: List<MoodEntry>): List<HashtagValue> {
        val overall = entries.mapNotNull { it.rating }.average()
        return HashtagAnalytics.snapshot(entries).stats.mapNotNull { stat ->
            val average = stat.averageRating ?: return@mapNotNull null
            if (stat.ratedEntryCount < MIN_HASHTAG_COUNT) null
            else HashtagValue(stat.tag, average, stat.entryCount)
        }.sortedWith(
            compareByDescending<HashtagValue> { it.count }
                .thenByDescending { abs(it.average - overall) }
                .thenBy { it.tag }
        ).take(2)
    }

    private fun interestingTimeBuckets(
        entries: List<MoodEntry>,
        settings: UserSettings,
        zoneId: ZoneId
    ): List<TimeBucket> {
        val window = MoodAnalytics.trackingWindow(settings)
        val duration = window.durationMinutes
        if (duration <= 0) return emptyList()

        val buckets = Array(3) { mutableListOf<Int>() }
        entries.forEach { entry ->
            val rating = entry.rating ?: return@forEach
            val offset = trackingOffsetMinutes(entry.localTime(zoneId), window) ?: return@forEach
            val index = ((offset.toDouble() / duration.toDouble()) * 3.0)
                .toInt()
                .coerceIn(0, 2)
            buckets[index].add(rating)
        }

        val boundaries = listOf(0, duration / 3, (duration * 2) / 3, duration)
        val values = (0..2).mapNotNull { index ->
            val ratings = buckets[index]
            if (ratings.size < MIN_TIME_BUCKET_COUNT) return@mapNotNull null
            TimeBucket(
                start = window.wakeTime.plusMinutes(boundaries[index].toLong()),
                end = window.wakeTime.plusMinutes(boundaries[index + 1].toLong()),
                average = ratings.average(),
                count = ratings.size
            )
        }

        if (values.size < 2) return emptyList()
        val spread = values.maxOf { it.average } - values.minOf { it.average }
        return if (spread >= MIN_INTERESTING_TIME_SPREAD) values else emptyList()
    }

    private fun trackingOffsetMinutes(
        time: LocalTime,
        window: MoodAnalytics.TrackingWindow
    ): Int? {
        val wakeMinutes = window.wakeTime.toSecondOfDay() / 60
        val timeMinutes = time.toSecondOfDay() / 60
        val offset = if (timeMinutes >= wakeMinutes) {
            timeMinutes - wakeMinutes
        } else {
            24 * 60 - wakeMinutes + timeMinutes
        }
        return offset.takeIf { it in 0..window.durationMinutes }
    }

    private fun MoodEntry.localDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()

    private fun MoodEntry.localTime(zoneId: ZoneId): LocalTime =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalTime()


    private fun List<Int>.averageOrNull(): Double? = if (isEmpty()) null else average()
    private fun roundToTenth(value: Double): Double = round(value * 10.0) / 10.0

    private const val MIN_HASHTAG_COUNT = 2
    private const val MIN_TIME_BUCKET_COUNT = 2
    private const val MIN_INTERESTING_TIME_SPREAD = 0.5
}
