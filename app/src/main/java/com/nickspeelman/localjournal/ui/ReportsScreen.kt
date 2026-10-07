package com.nickspeelman.localjournal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.analytics.MoodAnalytics
import com.nickspeelman.localjournal.analytics.RangeReportAnalytics
import com.nickspeelman.localjournal.data.HashtagUtils
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.export.ChartExportActions
import com.nickspeelman.localjournal.export.ReportExportActions
import com.nickspeelman.localjournal.export.ReportExportKind
import com.nickspeelman.localjournal.export.ShareableChart
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class ReportRangePreset(val label: String) {
    SEVEN_DAYS("7 days"),
    THIRTY_DAYS("30 days"),
    NINETY_DAYS("90 days"),
    ALL("All"),
    CUSTOM("Custom")
}

private enum class ReportType(val label: String) {
    TIME_OF_DAY("Time of day"),
    DAY_OF_WEEK("Day of week"),
    DAILY_TREND("Daily trend")
}

private data class SelectedDateRange(val start: LocalDate, val end: LocalDate)

private const val MIN_HISTORY_DAYS_FOR_30_DAY_TREND = 15L
private const val MIN_HISTORY_DAYS_FOR_90_DAY_TREND = 45L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    entries: List<MoodEntry>,
    settings: UserSettings,
    openWeeklyReport: Boolean = false,
    openMonthlyReport: Boolean = false,
    onOpenDayEntries: (LocalDate) -> Unit = {},
    onOpenWeekdayEntries: (DayOfWeek, LocalDate, LocalDate) -> Unit = { _, _, _ -> },
    onFreshAuthentication: (String, () -> Unit) -> Unit = { _, action -> action() }
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zoneId)
    var presetName by rememberSaveable {
        mutableStateOf(
            when {
                openWeeklyReport -> ReportRangePreset.SEVEN_DAYS.name
                openMonthlyReport -> ReportRangePreset.THIRTY_DAYS.name
                else -> ReportRangePreset.THIRTY_DAYS.name
            }
        )
    }
    var reportTypeName by rememberSaveable {
        mutableStateOf(
            if (openWeeklyReport || openMonthlyReport) ReportType.DAILY_TREND.name
            else ReportType.TIME_OF_DAY.name
        )
    }
    var customStartEpochDay by rememberSaveable { mutableStateOf(today.minusDays(29).toEpochDay()) }
    var customEndEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val preset = ReportRangePreset.valueOf(presetName)
    val reportType = ReportType.valueOf(reportTypeName)
    val customRange = SelectedDateRange(
        LocalDate.ofEpochDay(customStartEpochDay),
        LocalDate.ofEpochDay(customEndEpochDay)
    )

    val earliestEntryDate = remember(entries, zoneId) {
        entries.asSequence()
            .filter { it.rating != null }
            .minOfOrNull {
                Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
            }
    }
    val historySpanDays = earliestEntryDate?.let { earliest ->
        ChronoUnit.DAYS.between(earliest, today) + 1
    } ?: 0L
    val showThirtyDayTrend = historySpanDays >= MIN_HISTORY_DAYS_FOR_30_DAY_TREND || openMonthlyReport
    val showNinetyDayTrend = historySpanDays >= MIN_HISTORY_DAYS_FOR_90_DAY_TREND

    LaunchedEffect(reportType, showThirtyDayTrend, showNinetyDayTrend, presetName) {
        if (reportType == ReportType.DAILY_TREND) {
            when {
                preset == ReportRangePreset.NINETY_DAYS && !showNinetyDayTrend -> {
                    presetName = if (showThirtyDayTrend) {
                        ReportRangePreset.THIRTY_DAYS.name
                    } else {
                        ReportRangePreset.SEVEN_DAYS.name
                    }
                }
                preset == ReportRangePreset.THIRTY_DAYS && !showThirtyDayTrend -> {
                    presetName = ReportRangePreset.SEVEN_DAYS.name
                }
            }
        }
    }

    val range = remember(entries, preset, customRange, today) {
        when (preset) {
            ReportRangePreset.SEVEN_DAYS -> SelectedDateRange(today.minusDays(6), today)
            ReportRangePreset.THIRTY_DAYS -> SelectedDateRange(today.minusDays(29), today)
            ReportRangePreset.NINETY_DAYS -> SelectedDateRange(today.minusDays(89), today)
            ReportRangePreset.ALL -> {
                val earliest = entries.asSequence()
                    .filter { it.rating != null }
                    .minOfOrNull {
                        Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
                    } ?: today
                SelectedDateRange(earliest, today)
            }
            ReportRangePreset.CUSTOM -> customRange
        }
    }

    if (showDateRangePicker) {
        ReportDateRangeDialog(
            initialRange = customRange,
            onDismiss = { showDateRangePicker = false },
            onConfirm = { selected ->
                customStartEpochDay = selected.start.toEpochDay()
                customEndEpochDay = selected.end.toEpochDay()
                presetName = ReportRangePreset.CUSTOM.name
                showDateRangePicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Reports", style = MaterialTheme.typography.headlineMedium)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportRangePreset.entries
                .filter { option ->
                    reportType != ReportType.DAILY_TREND || when (option) {
                        ReportRangePreset.THIRTY_DAYS -> showThirtyDayTrend
                        ReportRangePreset.NINETY_DAYS -> showNinetyDayTrend
                        else -> true
                    }
                }
                .forEach { option ->
                FilterChip(
                    selected = preset == option,
                    onClick = {
                        if (option == ReportRangePreset.CUSTOM) {
                            customStartEpochDay = range.start.toEpochDay()
                            customEndEpochDay = range.end.toEpochDay()
                            showDateRangePicker = true
                        } else {
                            presetName = option.name
                        }
                    },
                    label = { Text(option.label) }
                )
            }
        }

        Text(
            text = formatDateRange(range.start, range.end),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val periodSummary = remember(entries, settings, range, zoneId) {
            RangeReportAnalytics.build(
                entries = entries,
                settings = settings,
                startDate = range.start,
                endDate = range.end,
                zoneId = zoneId
            )
        }
        PeriodSummaryCard(
            report = periodSummary,
            settings = settings
        )

        val reportExportKind = when (preset) {
            ReportRangePreset.SEVEN_DAYS -> ReportExportKind.WEEKLY
            ReportRangePreset.THIRTY_DAYS -> ReportExportKind.MONTHLY
            else -> ReportExportKind.RANGE
        }
        val reportExportTitle = when (preset) {
            ReportRangePreset.SEVEN_DAYS -> "Weekly report"
            ReportRangePreset.THIRTY_DAYS -> "Monthly report"
            ReportRangePreset.NINETY_DAYS -> "90-day report"
            ReportRangePreset.ALL -> "All-time report"
            ReportRangePreset.CUSTOM -> "Custom report"
        }
        ReportExportActions(
            kind = reportExportKind,
            title = reportExportTitle,
            entries = entries,
            settings = settings,
            startDate = range.start,
            endDate = range.end,
            onFreshAuthentication = onFreshAuthentication
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportType.entries.forEach { type ->
                FilterChip(
                    selected = reportType == type,
                    onClick = { reportTypeName = type.name },
                    label = { Text(type.label) }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (reportType) {
                    ReportType.TIME_OF_DAY -> TimeOfDayReport(entries, settings, range, zoneId)
                    ReportType.DAY_OF_WEEK -> WeekdayReport(entries, range, zoneId, onOpenWeekdayEntries)
                    ReportType.DAILY_TREND -> DailyTrendReport(
                        entries = entries,
                        range = range,
                        zoneId = zoneId,
                        trimLeadingEmptyDays = preset == ReportRangePreset.THIRTY_DAYS ||
                            preset == ReportRangePreset.NINETY_DAYS ||
                            preset == ReportRangePreset.CUSTOM,
                        previousPeriodDays = when (preset) {
                            ReportRangePreset.SEVEN_DAYS -> 7
                            ReportRangePreset.THIRTY_DAYS -> 30
                            else -> null
                        },
                        onOpenDayEntries = onOpenDayEntries
                    )
                }
            }
        }

        Text(
            text = when (reportType) {
                ReportType.TIME_OF_DAY -> "Time-of-day averages use only check-ins within your current wake-up-to-bedtime tracking window."
                ReportType.DAY_OF_WEEK -> "Each point is the average of all check-ins on that weekday in the selected period. Tap a point for details."
                ReportType.DAILY_TREND -> when (preset) {
                    ReportRangePreset.SEVEN_DAYS ->
                        "Daily points show observed averages. The smooth solid line shows the current 7-day trend; the dashed line compares the previous 7 days. Tap a point for details."
                    ReportRangePreset.THIRTY_DAYS ->
                        "Daily points show observed averages. The smooth solid line shows the current 30-day trend; the dashed line compares the previous 30 days. Tap a point for details."
                    else ->
                        "Daily points show observed averages. The line is a smoothed trend sized to the selected range. Tap a point for details."
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PeriodSummaryCard(
    report: RangeReportAnalytics.Report?,
    settings: UserSettings
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Period summary", style = MaterialTheme.typography.titleMedium)
            if (report == null) {
                Text(
                    "No rated check-ins in this period yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val averageText = String.format(Locale.getDefault(), "%.1f", report.average)
            val comparison = report.previousAverage?.let { previous ->
                val previousText = String.format(Locale.getDefault(), "%.1f", previous)
                val delta = report.displayedDelta ?: 0.0
                val change = when {
                    delta > 0.0 -> "↑ ${String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(delta))}"
                    delta < 0.0 -> "↓ ${String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(delta))}"
                    else -> "→ no change"
                }
                "$averageText average · previous $previousText · $change"
            } ?: "$averageText average · no check-ins in the previous period"

            Text(comparison, style = MaterialTheme.typography.titleSmall)
            Text(
                "${report.checkInCount} ${if (report.checkInCount == 1) "check-in" else "check-ins"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (report.highest != null || report.lowest != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryMetric(
                        label = "Highest",
                        value = report.highest?.let {
                            "${it.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))} · ${String.format(Locale.getDefault(), "%.1f", it.average)}"
                        } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetric(
                        label = "Lowest",
                        value = report.lowest?.let {
                            "${it.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))} · ${String.format(Locale.getDefault(), "%.1f", it.average)}"
                        } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (report.hashtags.isNotEmpty()) {
                Text("Top hashtags", style = MaterialTheme.typography.labelLarge)
                report.hashtags.forEach { hashtag ->
                    Text(
                        "${hashtag.tag}  ${String.format(Locale.getDefault(), "%.1f", hashtag.average)} · ${hashtag.count}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (report.timeBuckets.isNotEmpty()) {
                Text("Time of day", style = MaterialTheme.typography.labelLarge)
                report.timeBuckets.forEach { bucket ->
                    Text(
                        "${formatTime(bucket.start, settings.use24Hour)}–${formatTime(bucket.end, settings.use24Hour)}  " +
                            "${String.format(Locale.getDefault(), "%.1f", bucket.average)} · ${bucket.count}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimeOfDayReport(
    entries: List<MoodEntry>,
    settings: UserSettings,
    range: SelectedDateRange,
    zoneId: ZoneId
) {
    val series = remember(entries, settings, range, zoneId) {
        MoodAnalytics.timeOfDaySeries(entries, range.start, range.end, settings, zoneId)
    }
    val window = remember(settings) { MoodAnalytics.trackingWindow(settings) }
    val count = remember(entries, settings, range, zoneId) {
        MoodAnalytics.entriesInTrackingWindowCount(entries, range.start, range.end, settings, zoneId)
    }
    val values = series.map { it.average }
    val midpoint = window.wakeTime.plusMinutes(window.durationMinutes / 2L)

    Text("Average mood by time of day", style = MaterialTheme.typography.titleMedium)
    Text(
        "Tracking window: ${formatTime(window.wakeTime, settings.use24Hour)} – ${formatTime(window.bedtime, settings.use24Hour)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    MoodChart(
        values = values,
        xLabels = listOf(
            formatTime(window.wakeTime, settings.use24Hour),
            formatTime(midpoint, settings.use24Hour),
            formatTime(window.bedtime, settings.use24Hour)
        )
    )
    ReportSampleCount(count)
    val summary = remember(entries, settings, range, zoneId) {
        RangeReportAnalytics.build(entries, settings, range.start, range.end, zoneId)
    }
    ChartExportActions(
        ShareableChart(
            title = "Average mood by time of day", startDate = range.start, endDate = range.end,
            checkInCount = count, averageRating = summary?.average, values = values,
            xLabels = listOf(formatTime(window.wakeTime, settings.use24Hour), formatTime(midpoint, settings.use24Hour), formatTime(window.bedtime, settings.use24Hour))
        )
    )
}

@Composable
private fun WeekdayReport(
    entries: List<MoodEntry>,
    range: SelectedDateRange,
    zoneId: ZoneId,
    onOpenWeekdayEntries: (DayOfWeek, LocalDate, LocalDate) -> Unit
) {
    val series = remember(entries, range, zoneId) {
        MoodAnalytics.weekdaySeries(entries, range.start, range.end, zoneId)
    }
    val count = series.sumOf { it.count }
    var selectedWeekday by remember { mutableStateOf<MoodAnalytics.WeekdayPoint?>(null) }

    selectedWeekday?.let { point ->
        val weekdayEntries = remember(entries, point.day, range, zoneId) {
            entries.filter { entry ->
                val date = Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
                entry.rating != null &&
                    !date.isBefore(range.start) && !date.isAfter(range.end) && date.dayOfWeek == point.day
            }
        }
        val topHashtags = remember(weekdayEntries) { topHashtagsForEntries(weekdayEntries) }
        WeekdayPointDialog(
            point = point,
            range = range,
            topHashtags = topHashtags,
            onDismiss = { selectedWeekday = null },
            onViewEntries = {
                selectedWeekday = null
                onOpenWeekdayEntries(point.day, range.start, range.end)
            }
        )
    }

    Text("Average mood by day of week", style = MaterialTheme.typography.titleMedium)
    MoodChart(
        values = series.map { it.average },
        xLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
        showPoints = true,
        onPointSelected = { index ->
            series.getOrNull(index)?.takeIf { it.average != null }?.let { selectedWeekday = it }
        }
    )
    ReportSampleCount(count)
    val summary = remember(entries, range, zoneId) {
        val ratings = entries.filter { e ->
            val d = Instant.ofEpochMilli(e.timestamp).atZone(zoneId).toLocalDate()
            e.rating != null && !d.isBefore(range.start) && !d.isAfter(range.end)
        }.mapNotNull { it.rating }
        if (ratings.isEmpty()) null else ratings.average()
    }
    ChartExportActions(
        ShareableChart(
            title = "Average mood by day of week", startDate = range.start, endDate = range.end,
            checkInCount = count, averageRating = summary, values = series.map { it.average },
            xLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        )
    )
}

@Composable
private fun DailyTrendReport(
    entries: List<MoodEntry>,
    range: SelectedDateRange,
    zoneId: ZoneId,
    trimLeadingEmptyDays: Boolean,
    previousPeriodDays: Int?,
    onOpenDayEntries: (LocalDate) -> Unit
) {
    val fullSeries = remember(entries, range, zoneId) {
        MoodAnalytics.dailySeries(entries, range.start, range.end, zoneId)
    }
    val series = remember(fullSeries, trimLeadingEmptyDays) {
        if (trimLeadingEmptyDays && fullSeries.any { it.average != null }) {
            fullSeries.dropWhile { it.average == null }
        } else {
            fullSeries
        }
    }
    val chartStart = series.firstOrNull()?.date ?: range.start
    val chartEnd = series.lastOrNull()?.date ?: range.end
    val dayCount = ChronoUnit.DAYS.between(chartStart, chartEnd) + 1
    val trendWindowDays = MoodAnalytics.adaptiveTrendWindowDays(dayCount.toInt())
    val trendValues = remember(series, trendWindowDays) {
        MoodAnalytics.dailyTrendValues(series, trendWindowDays)
    }
    val fullPreviousSeries = remember(entries, range, zoneId, previousPeriodDays) {
        if (previousPeriodDays != null) {
            MoodAnalytics.dailySeries(
                entries,
                range.start.minusDays(previousPeriodDays.toLong()),
                range.end.minusDays(previousPeriodDays.toLong()),
                zoneId
            )
        } else {
            emptyList()
        }
    }
    // Keep the comparison line aligned with a shortened current x-axis, while comparison
    // statistics still use the complete preceding 7/30-day period.
    val previousSeries = remember(fullPreviousSeries, fullSeries, series) {
        val droppedLeadingDays = (fullSeries.size - series.size).coerceAtLeast(0)
        if (droppedLeadingDays == 0) fullPreviousSeries
        else fullPreviousSeries.drop(droppedLeadingDays.coerceAtMost(fullPreviousSeries.size))
    }
    val previousTrendValues = remember(previousSeries, trendWindowDays) {
        if (previousSeries.isEmpty()) null
        else MoodAnalytics.dailyTrendValues(previousSeries, trendWindowDays)
    }
    fun weightedAverage(points: List<MoodAnalytics.DailyPoint>): Double? {
        var weighted = 0.0
        var total = 0
        points.forEach { point ->
            val average = point.average ?: return@forEach
            if (point.count <= 0) return@forEach
            weighted += average * point.count
            total += point.count
        }
        return if (total == 0) null else weighted / total
    }
    val currentAverage = remember(series) { weightedAverage(series) }
    val previousAverage = remember(fullPreviousSeries) { weightedAverage(fullPreviousSeries) }
    val count = series.sumOf { it.count }
    val middleDate = chartStart.plusDays((dayCount - 1) / 2)
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    var selectedDay by remember { mutableStateOf<MoodAnalytics.DailyPoint?>(null) }

    selectedDay?.let { day ->
        val dayEntries = remember(entries, day.date, zoneId) {
            entries.filter { entry ->
                entry.rating != null &&
                    Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate() == day.date
            }
        }
        val topHashtags = remember(dayEntries) { topHashtagsForEntries(dayEntries) }
        DailyTrendPointDialog(
            day = day,
            topHashtags = topHashtags,
            onDismiss = { selectedDay = null },
            onViewEntries = {
                selectedDay = null
                onOpenDayEntries(day.date)
            }
        )
    }

    Text("Average mood by day", style = MaterialTheme.typography.titleMedium)
    if (previousPeriodDays != null && currentAverage != null) {
        val currentText = String.format(Locale.getDefault(), "%.1f", currentAverage)
        val comparisonText = previousAverage?.let { previous ->
            val previousText = String.format(Locale.getDefault(), "%.1f", previous)
            // Match the weekly notification: compare the displayed tenths rather than showing a
            // delta that appears inconsistent with the rounded averages beside it.
            val currentDisplayed = kotlin.math.round(currentAverage * 10.0) / 10.0
            val previousDisplayed = kotlin.math.round(previous * 10.0) / 10.0
            val delta = kotlin.math.round((currentDisplayed - previousDisplayed) * 10.0) / 10.0
            val deltaText = String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(delta))
            val change = when {
                delta == 0.0 -> "no change"
                delta > 0 -> "↑ $deltaText"
                else -> "↓ $deltaText"
            }
            "$previousPeriodDays-day average $currentText · Previous $previousPeriodDays days $previousText · $change"
        } ?: "$previousPeriodDays-day average $currentText · No check-ins in the previous $previousPeriodDays days"
        Text(
            text = comparisonText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    MoodChart(
        values = series.map { it.average },
        trendValues = trendValues,
        comparisonTrendValues = previousTrendValues,
        xLabels = listOf(
            chartStart.format(dateFormatter),
            middleDate.format(dateFormatter),
            chartEnd.format(dateFormatter)
        ),
        showPoints = true,
        connectValuePoints = false,
        valuesColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        trendColor = MaterialTheme.colorScheme.primary,
        trendDashed = false,
        trendLineWidthDp = 4f,
        comparisonTrendColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        comparisonTrendDashed = true,
        smoothTrendLines = previousPeriodDays != null,
        onPointSelected = { index ->
            series.getOrNull(index)?.takeIf { it.average != null }?.let { selectedDay = it }
        }
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (previousPeriodDays != null) "Current $previousPeriodDays days" else "${trendWindowDays}-day trend",
            style = MaterialTheme.typography.labelSmall
        )
        if (previousPeriodDays != null && previousAverage != null) {
            Spacer(Modifier.width(16.dp))
            Text("┄┄", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("Previous $previousPeriodDays days", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(8.dp))
        Text("Daily average", style = MaterialTheme.typography.labelSmall)
    }
    ReportSampleCount(count)
    ChartExportActions(
        ShareableChart(
            title = "Mood trend", startDate = chartStart, endDate = chartEnd,
            checkInCount = count, averageRating = currentAverage, values = series.map { it.average },
            trendValues = trendValues, comparisonTrendValues = previousTrendValues,
            xLabels = listOf(chartStart.format(dateFormatter), middleDate.format(dateFormatter), chartEnd.format(dateFormatter)),
            trendLabel = if (previousPeriodDays != null) "Current $previousPeriodDays days" else "$trendWindowDays-day trend",
            comparisonLabel = previousPeriodDays?.let { "Previous $it days" }
        )
    )
}

private fun topHashtagsForEntries(entries: List<MoodEntry>): List<String> {
    val counts = linkedMapOf<String, Int>()
    entries.forEach { entry ->
        HashtagUtils.parse(entry.hashtags)
            .distinct()
            .forEach { tag ->
                counts[tag] = (counts[tag] ?: 0) + 1
            }
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(3)
        .map { it.key }
}

@Composable
private fun WeekdayPointDialog(
    point: MoodAnalytics.WeekdayPoint,
    range: SelectedDateRange,
    topHashtags: List<String>,
    onDismiss: () -> Unit,
    onViewEntries: () -> Unit
) {
    val dayName = point.day.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    val rangeFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = point.average?.let { "Average mood: ${String.format(Locale.getDefault(), "%.1f", it)}" } ?: "No rated check-ins",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${point.count} ${if (point.count == 1) "check-in" else "check-ins"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${range.start.format(rangeFormatter)} – ${range.end.format(rangeFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (topHashtags.isNotEmpty()) {
                    Text(
                        text = "Top hashtags: ${topHashtags.joinToString("  ")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onViewEntries) { Text("View entries") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DailyTrendPointDialog(
    day: MoodAnalytics.DailyPoint,
    topHashtags: List<String>,
    onDismiss: () -> Unit,
    onViewEntries: () -> Unit
) {
    val titleFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.date.format(titleFormatter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = day.average?.let { "Average mood: ${String.format(Locale.getDefault(), "%.1f", it)}" } ?: "No rated check-ins",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${day.count} ${if (day.count == 1) "check-in" else "check-ins"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (topHashtags.isNotEmpty()) {
                    Text(
                        text = "Top hashtags: ${topHashtags.joinToString("  ")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onViewEntries) { Text("View entries") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ReportSampleCount(count: Int) {
    Text(
        text = "Based on $count ${if (count == 1) "check-in" else "check-ins"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MoodChart(
    values: List<Double?>,
    xLabels: List<String>,
    trendValues: List<Double?>? = null,
    comparisonTrendValues: List<Double?>? = null,
    showPoints: Boolean = false,
    connectValuePoints: Boolean = true,
    valuesColor: Color? = null,
    trendColor: Color? = null,
    trendDashed: Boolean = true,
    trendLineWidthDp: Float = 2f,
    comparisonTrendColor: Color? = null,
    comparisonTrendDashed: Boolean = true,
    smoothTrendLines: Boolean = false,
    onPointSelected: ((Int) -> Unit)? = null
) {
    if (values.none { it != null }) {
        Text(
            text = "Not enough data yet. Your trend will appear as your journal grows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val lineColor = valuesColor ?: MaterialTheme.colorScheme.primary
    val actualTrendColor = trendColor ?: MaterialTheme.colorScheme.tertiary
    val actualComparisonColor = comparisonTrendColor ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
    ) {
        Column(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .padding(top = 3.dp, bottom = 3.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            (5 downTo 1).forEach { mood ->
                Text("$mood", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(values, onPointSelected) {
                    if (onPointSelected != null && values.isNotEmpty()) {
                        detectTapGestures { tap ->
                            val top = 8f
                            val bottom = size.height.toFloat() - 8f
                            val chartHeight = bottom - top
                            val denominator = (values.size - 1).coerceAtLeast(1).toFloat()
                            val threshold = 24.dp.toPx()

                            val nearest = values.mapIndexedNotNull { index, value ->
                                value ?: return@mapIndexedNotNull null
                                val x = size.width.toFloat() * index / denominator
                                val clipped = value.coerceIn(1.0, 5.0)
                                val y = bottom - (((clipped - 1.0) / 4.0).toFloat() * chartHeight)
                                index to Offset(tap.x - x, tap.y - y).getDistance()
                            }.minByOrNull { it.second }

                            if (nearest != null && nearest.second <= threshold) {
                                onPointSelected?.invoke(nearest.first)
                            }
                        }
                    }
                }
        ) {
            val top = 8f
            val bottom = size.height - 8f
            val height = bottom - top

            for (mood in 1..5) {
                val y = bottom - ((mood - 1) / 4f) * height
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            fun drawSeries(
                series: List<Double?>,
                color: androidx.compose.ui.graphics.Color,
                drawLine: Boolean,
                dashed: Boolean,
                points: Boolean,
                lineWidthDp: Float,
                connectAcrossNulls: Boolean = false,
                smooth: Boolean = false
            ) {
                if (series.isEmpty()) return
                val denominator = (series.size - 1).coerceAtLeast(1).toFloat()
                val stroke = Stroke(
                    width = lineWidthDp.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null
                )

                fun offsetFor(index: Int, value: Double): Offset {
                    val x = size.width * index / denominator
                    val clipped = value.coerceIn(1.0, 5.0)
                    val y = bottom - (((clipped - 1.0) / 4.0).toFloat() * height)
                    return Offset(x, y)
                }

                fun drawSegment(pointsToDraw: List<Offset>) {
                    if (!drawLine || pointsToDraw.isEmpty()) return
                    val path = Path().apply { moveTo(pointsToDraw.first().x, pointsToDraw.first().y) }
                    if (smooth && pointsToDraw.size >= 3) {
                        for (i in 0 until pointsToDraw.lastIndex) {
                            val p0 = if (i == 0) pointsToDraw[i] else pointsToDraw[i - 1]
                            val p1 = pointsToDraw[i]
                            val p2 = pointsToDraw[i + 1]
                            val p3 = if (i + 2 <= pointsToDraw.lastIndex) pointsToDraw[i + 2] else p2
                            val c1 = Offset(
                                p1.x + (p2.x - p0.x) / 6f,
                                (p1.y + (p2.y - p0.y) / 6f).coerceIn(top, bottom)
                            )
                            val c2 = Offset(
                                p2.x - (p3.x - p1.x) / 6f,
                                (p2.y - (p3.y - p1.y) / 6f).coerceIn(top, bottom)
                            )
                            path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
                        }
                    } else {
                        pointsToDraw.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
                    }
                    drawPath(path = path, color = color, style = stroke)
                }

                val activeSegment = mutableListOf<Offset>()
                series.forEachIndexed { index, value ->
                    if (value == null) {
                        if (!connectAcrossNulls) {
                            drawSegment(activeSegment)
                            activeSegment.clear()
                        }
                    } else {
                        val point = offsetFor(index, value)
                        activeSegment += point
                        if (points) {
                            val radius = if (series.size <= 31) 3.5.dp.toPx() else 2.dp.toPx()
                            drawCircle(color = color, radius = radius, center = point)
                        }
                    }
                }
                drawSegment(activeSegment)
            }

            drawSeries(
                values,
                lineColor,
                drawLine = connectValuePoints,
                dashed = false,
                points = showPoints,
                lineWidthDp = 3f
            )
            comparisonTrendValues?.let {
                drawSeries(
                    it,
                    actualComparisonColor,
                    drawLine = true,
                    dashed = comparisonTrendDashed,
                    points = false,
                    lineWidthDp = 3f,
                    connectAcrossNulls = true,
                    smooth = smoothTrendLines
                )
            }
            trendValues?.let {
                drawSeries(
                    it,
                    actualTrendColor,
                    drawLine = true,
                    dashed = trendDashed,
                    points = false,
                    lineWidthDp = trendLineWidthDp,
                    connectAcrossNulls = true,
                    smooth = smoothTrendLines
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        xLabels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDateRangeDialog(
    initialRange: SelectedDateRange,
    onDismiss: () -> Unit,
    onConfirm: (SelectedDateRange) -> Unit
) {
    fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange.start.utcMillis(),
        initialSelectedEndDateMillis = initialRange.end.utcMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.utcDate() ?: return@TextButton
                    val end = state.selectedEndDateMillis?.utcDate() ?: return@TextButton
                    onConfirm(SelectedDateRange(start, end))
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
        )
    }
}

private fun formatDateRange(start: LocalDate, end: LocalDate): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return "${start.format(formatter)} – ${end.format(formatter)}"
}

private fun formatTime(time: LocalTime, use24Hour: Boolean): String {
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
