package com.nickspeelman.localjournal.analytics

import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.round

/**
 * Pure calculations used by the Home and Reports screens.
 *
 * Keeping these calculations outside the ViewModel/UI makes the reporting rules explicit and
 * unit-testable while leaving the persisted MoodEntry format unchanged.
 */
object MoodAnalytics {

    data class ComparisonMetric(
        val current: Double? = null,
        val previous: Double? = null
    ) {
        val direction: TrendDirection
            get() {
                val currentRounded = current?.roundToDisplayedTenth() ?: return TrendDirection.NONE
                val previousRounded = previous?.roundToDisplayedTenth() ?: return TrendDirection.NONE
                return when {
                    currentRounded > previousRounded -> TrendDirection.UP
                    currentRounded < previousRounded -> TrendDirection.DOWN
                    else -> TrendDirection.FLAT
                }
            }
    }

    enum class TrendDirection { UP, DOWN, FLAT, NONE }

    data class HomeSummary(
        val last30Days: ComparisonMetric = ComparisonMetric(),
        val today: ComparisonMetric = ComparisonMetric(),
        val last7Days: ComparisonMetric = ComparisonMetric(),
        val topHashtags: List<HashtagStat> = emptyList()
    )

    data class TimeOfDayPoint(
        /** Minutes from the configured wake time. */
        val offsetMinutes: Double,
        val average: Double?
    )

    data class WeekdayPoint(
        val day: DayOfWeek,
        val average: Double?,
        val count: Int
    )

    data class DailyPoint(
        val date: LocalDate,
        val average: Double?,
        val count: Int,
        val movingAverage7Day: Double?
    )

    data class TrackingWindow(
        val wakeTime: LocalTime,
        val bedtime: LocalTime,
        val durationMinutes: Int
    )

    fun homeSummary(
        entries: List<MoodEntry>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): HomeSummary {
        fun averageBetween(startInclusive: LocalDate, endInclusive: LocalDate): Double? {
            val ratings = entries.asSequence()
                .filter {
                    val date = it.localDate(zoneId)
                    !date.isBefore(startInclusive) && !date.isAfter(endInclusive)
                }
                .mapNotNull { it.rating }
                .toList()
            return ratings.averageOrNull()
        }

        val current30Start = today.minusDays(29)
        val previous30End = current30Start.minusDays(1)
        val previous30Start = previous30End.minusDays(29)

        val current7Start = today.minusDays(6)
        val previous7End = current7Start.minusDays(1)
        val previous7Start = previous7End.minusDays(6)

        val hashtagStats = HashtagAnalytics.filterAndSort(
            stats = HashtagAnalytics.snapshot(entries, zoneId = zoneId).stats,
            query = "",
            sort = HashtagSort.MOST_USED
        ).take(5)

        return HomeSummary(
            last30Days = ComparisonMetric(
                current = averageBetween(current30Start, today),
                previous = averageBetween(previous30Start, previous30End)
            ),
            today = ComparisonMetric(
                current = averageBetween(today, today),
                previous = averageBetween(today.minusDays(1), today.minusDays(1))
            ),
            last7Days = ComparisonMetric(
                current = averageBetween(current7Start, today),
                previous = averageBetween(previous7Start, previous7End)
            ),
            topHashtags = hashtagStats
        )
    }

    fun trackingWindow(settings: UserSettings): TrackingWindow {
        val wake = LocalTime.of(settings.sleepEndHour, settings.sleepEndMinute)
        val bedtime = LocalTime.of(settings.sleepStartHour, settings.sleepStartMinute)
        val wakeMinutes = wake.toSecondOfDay() / 60
        val bedMinutes = bedtime.toSecondOfDay() / 60
        val duration = if (bedMinutes > wakeMinutes) {
            bedMinutes - wakeMinutes
        } else {
            24 * 60 - wakeMinutes + bedMinutes
        }
        return TrackingWindow(wake, bedtime, duration)
    }

    /**
     * Smooth mood by time of day using a Gaussian-weighted local average. The curve only spans
     * the configured waking/tracking window (wake-up to bedtime), never a fixed 24-hour cycle.
     * Points with no observations within twice the smoothing bandwidth remain missing.
     */
    fun timeOfDaySeries(
        entries: List<MoodEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
        settings: UserSettings,
        zoneId: ZoneId = ZoneId.systemDefault(),
        sampleCount: Int = 49
    ): List<TimeOfDayPoint> {
        val window = trackingWindow(settings)
        val observations = entries.asSequence()
            .map { it to it.localDateTime(zoneId) }
            .filter { (_, dateTime) ->
                !dateTime.toLocalDate().isBefore(startDate) &&
                    !dateTime.toLocalDate().isAfter(endDate)
            }
            .mapNotNull { (entry, dateTime) ->
                val rating = entry.rating ?: return@mapNotNull null
                val offset = trackingOffsetMinutes(dateTime.toLocalTime(), window) ?: return@mapNotNull null
                offset.toDouble() to rating.toDouble()
            }
            .toList()

        if (sampleCount < 2) return emptyList()
        val duration = window.durationMinutes.toDouble()
        // Scale smoothing to the user's waking window, but keep it in a sensible range.
        val bandwidth = (duration / 8.0).coerceIn(45.0, 120.0)
        val radius = bandwidth * 2.0

        return (0 until sampleCount).map { index ->
            val x = duration * index / (sampleCount - 1).toDouble()
            val nearby = observations.mapNotNull { (obsX, rating) ->
                val distance = kotlin.math.abs(obsX - x)
                if (distance > radius) null
                else {
                    val weight = exp(-0.5 * (distance / bandwidth) * (distance / bandwidth))
                    weight to rating
                }
            }
            val weightSum = nearby.sumOf { it.first }
            val average = if (weightSum > 0.0) {
                nearby.sumOf { (weight, rating) -> weight * rating } / weightSum
            } else null
            TimeOfDayPoint(x, average)
        }
    }

    fun weekdaySeries(
        entries: List<MoodEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<WeekdayPoint> {
        val byDay = entries.asSequence()
            .filter { it.rating != null }
            .filter {
                val date = it.localDate(zoneId)
                !date.isBefore(startDate) && !date.isAfter(endDate)
            }
            .groupBy { it.localDate(zoneId).dayOfWeek }

        return DayOfWeek.values().map { day ->
            val dayEntries = byDay[day].orEmpty()
            WeekdayPoint(
                day = day,
                average = dayEntries.mapNotNull { it.rating }.averageOrNull(),
                count = dayEntries.count { it.rating != null }
            )
        }
    }

    fun dailySeries(
        entries: List<MoodEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DailyPoint> {
        if (endDate.isBefore(startDate)) return emptyList()
        val ratingsByDate = entries.asSequence()
            .filter { it.rating != null }
            .filter {
                val date = it.localDate(zoneId)
                !date.isBefore(startDate) && !date.isAfter(endDate)
            }
            .groupBy { it.localDate(zoneId) }
            .mapValues { (_, dayEntries) -> dayEntries.mapNotNull { it.rating } }

        val dates = generateSequence(startDate) { date ->
            date.plusDays(1).takeUnless { it.isAfter(endDate) }
        }.toList()

        return dates.mapIndexed { index, date ->
            val ratings = ratingsByDate[date].orEmpty()
            // Calendar 7-day trailing average. Missing days stay missing rather than becoming zero;
            // available observations in the 7-day window contribute to the trend.
            val windowStart = max(0, index - 6)
            val movingRatings = dates.subList(windowStart, index + 1)
                .flatMap { ratingsByDate[it].orEmpty() }

            DailyPoint(
                date = date,
                average = ratings.averageOrNull(),
                count = ratings.size,
                movingAverage7Day = movingRatings.averageOrNull()
            )
        }
    }


    /**
     * Pick a smoothing window that stays useful at the currently displayed scale. Short ranges
     * should still have a visible trend, while long ranges need more smoothing to avoid noise.
     */
    fun adaptiveTrendWindowDays(dayCount: Int): Int = when {
        dayCount <= 10 -> 3
        dayCount <= 45 -> 7
        dayCount <= 120 -> 14
        else -> 30
    }

    /**
     * Weighted centered trend from daily aggregates. Missing calendar days are not treated as
     * zero; actual check-ins near each date contribute in proportion to their count. Centering
     * the window keeps short-range trends intuitive and lets an isolated observed day influence
     * neighboring trend positions without pretending that a daily average was measured there.
     */
    fun dailyTrendValues(
        series: List<DailyPoint>,
        windowDays: Int
    ): List<Double?> {
        if (series.isEmpty()) return emptyList()
        val window = windowDays.coerceAtLeast(1)
        val before = (window - 1) / 2
        val after = window - before - 1
        return series.indices.map { index ->
            val start = max(0, index - before)
            val end = minOf(series.lastIndex, index + after)
            var weightedSum = 0.0
            var totalCount = 0
            for (i in start..end) {
                val point = series[i]
                val average = point.average ?: continue
                if (point.count <= 0) continue
                weightedSum += average * point.count
                totalCount += point.count
            }
            if (totalCount == 0) null else weightedSum / totalCount
        }
    }

    fun entriesInTrackingWindowCount(
        entries: List<MoodEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
        settings: UserSettings,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        val window = trackingWindow(settings)
        return entries.count { entry ->
            if (entry.rating == null) return@count false
            val dateTime = entry.localDateTime(zoneId)
            !dateTime.toLocalDate().isBefore(startDate) &&
                !dateTime.toLocalDate().isAfter(endDate) &&
                trackingOffsetMinutes(dateTime.toLocalTime(), window) != null
        }
    }

    fun entriesInRangeCount(
        entries: List<MoodEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int = entries.count {
        if (it.rating == null) return@count false
        val date = it.localDate(zoneId)
        !date.isBefore(startDate) && !date.isAfter(endDate)
    }

    private fun trackingOffsetMinutes(time: LocalTime, window: TrackingWindow): Int? {
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

    private fun MoodEntry.localDateTime(zoneId: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDateTime()


    private fun List<Int>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun Double.roundToDisplayedTenth(): Double = round(this * 10.0) / 10.0
}
