package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.round

/** Pure calculations for the lock-screen weekly report. */
object WeeklyReportAnalytics {

    data class DayValue(
        val day: DayOfWeek,
        val average: Double?,
        val count: Int
    )

    data class DayHighlight(
        val day: DayOfWeek,
        val average: Double
    )

    data class HashtagValue(
        val tag: String,
        val average: Double,
        val count: Int
    )

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
        val currentDays: List<DayValue>,
        val previousDays: List<DayValue>,
        val highest: DayHighlight?,
        val lowest: DayHighlight?,
        val hashtags: List<HashtagValue>,
        val timeBuckets: List<TimeBucket>
    ) {
        val displayedDelta: Double?
            get() = previousAverage?.let {
                roundToTenth(average) - roundToTenth(it)
            }?.let { roundToTenth(it) }
    }

    /**
     * A scheduled weekly report represents the most recent Monday-Sunday week. If this runs on
     * Sunday it includes today; if WorkManager delivers it late on Monday, it still reports the
     * Sunday that just ended rather than silently shifting the window.
     */
    fun mostRecentSunday(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun build(
        entries: List<MoodEntry>,
        settings: UserSettings,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Report? {
        val startDate = endDate.minusDays(6)
        val previousEnd = startDate.minusDays(1)
        val previousStart = previousEnd.minusDays(6)

        val currentEntries = entries.filter { it.rating != null && it.localDate(zoneId) in startDate..endDate }
        if (currentEntries.isEmpty()) return null
        val previousEntries = entries.filter { it.rating != null && it.localDate(zoneId) in previousStart..previousEnd }

        val currentDays = dayValues(currentEntries, startDate, zoneId)
        val previousDays = dayValues(previousEntries, previousStart, zoneId)
        val observedDays = currentDays.filter { it.average != null }

        val highest = if (observedDays.size >= 2) {
            observedDays.maxByOrNull { it.average!! }?.let { DayHighlight(it.day, it.average!!) }
        } else null
        val lowest = if (observedDays.size >= 2) {
            observedDays.minByOrNull { it.average!! }?.let { DayHighlight(it.day, it.average!!) }
        } else null

        return Report(
            startDate = startDate,
            endDate = endDate,
            average = currentEntries.mapNotNull { it.rating }.average(),
            previousAverage = previousEntries.mapNotNull { it.rating }.averageOrNull(),
            checkInCount = currentEntries.size,
            currentDays = currentDays,
            previousDays = previousDays,
            highest = highest,
            lowest = lowest,
            hashtags = topHashtags(currentEntries),
            timeBuckets = interestingTimeBuckets(currentEntries, settings, zoneId)
        )
    }

    private fun dayValues(
        entries: List<MoodEntry>,
        monday: LocalDate,
        zoneId: ZoneId
    ): List<DayValue> = (0L..6L).map { offset ->
        val date = monday.plusDays(offset)
        val dayEntries = entries.filter { it.localDate(zoneId) == date }
        DayValue(
            day = date.dayOfWeek,
            average = dayEntries.mapNotNull { it.rating }.averageOrNull(),
            count = dayEntries.size
        )
    }

    /**
     * Three-day weighted centered trend for the compact weekly chart. Missing days are not zero;
     * nearby observed check-ins contribute so sparse logging still produces an intuitive trend.
     */
    fun trendValues(days: List<DayValue>, windowDays: Int = 3): List<Double?> {
        if (days.isEmpty()) return emptyList()
        val window = windowDays.coerceAtLeast(1)
        val before = (window - 1) / 2
        val after = window - before - 1
        return days.indices.map { index ->
            val start = (index - before).coerceAtLeast(0)
            val end = (index + after).coerceAtMost(days.lastIndex)
            var weightedSum = 0.0
            var totalCount = 0
            for (i in start..end) {
                val day = days[i]
                val average = day.average ?: continue
                if (day.count <= 0) continue
                weightedSum += average * day.count
                totalCount += day.count
            }
            if (totalCount == 0) null else weightedSum / totalCount
        }
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

    /**
     * Split the configured waking/tracking window into thirds. The block is omitted unless at
     * least two thirds have two or more check-ins and their averages differ by >= 0.5 points.
     * This keeps time-of-day data out of the notification when it is mostly noise.
     */
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
            val time = entry.localTime(zoneId)
            val offset = trackingOffsetMinutes(time, window) ?: return@forEach
            val index = ((offset.toDouble() / duration.toDouble()) * 3.0)
                .toInt()
                .coerceIn(0, 2)
            buckets[index].add(rating)
        }

        val segmentBoundaries = listOf(
            0,
            duration / 3,
            (duration * 2) / 3,
            duration
        )

        val values = (0..2).mapNotNull { index ->
            val ratings = buckets[index]
            if (ratings.size < MIN_TIME_BUCKET_COUNT) return@mapNotNull null
            TimeBucket(
                start = window.wakeTime.plusMinutes(segmentBoundaries[index].toLong()),
                end = window.wakeTime.plusMinutes(segmentBoundaries[index + 1].toLong()),
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
