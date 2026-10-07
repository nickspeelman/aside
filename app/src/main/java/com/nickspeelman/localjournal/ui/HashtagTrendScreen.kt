package com.nickspeelman.localjournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.analytics.HashtagDateRange
import com.nickspeelman.localjournal.analytics.MoodAnalytics
import com.nickspeelman.localjournal.data.HashtagUtils
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.export.ChartExportActions
import com.nickspeelman.localjournal.export.ShareableChart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class HashtagTrendRange(val label: String) {
    SEVEN_DAYS("7 days"),
    THIRTY_DAYS("30 days"),
    NINETY_DAYS("90 days"),
    ALL("All"),
    CUSTOM("Custom")
}


private const val HASHTAG_MIN_HISTORY_DAYS_FOR_30 = 15L
private const val HASHTAG_MIN_HISTORY_DAYS_FOR_90 = 45L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagTrendScreen(
    entries: List<MoodEntry>,
    tag: String,
    onClose: () -> Unit,
    initialRange: HashtagDateRange? = null,
    initialAllTime: Boolean = false
) {
    BackHandler(onBack = onClose)

    val zoneId = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zoneId)
    val normalizedTag = remember(tag) { HashtagUtils.normalize(tag) }
    val taggedEntries = remember(entries, normalizedTag) {
        entries.filter { HashtagUtils.contains(it.hashtags, normalizedTag) }
    }
    val ratedTaggedEntries = remember(taggedEntries) { taggedEntries.filter { it.rating != null } }

    val earliestRatedDate = remember(ratedTaggedEntries, zoneId) {
        ratedTaggedEntries.minOfOrNull {
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
        }
    }
    val historySpanDays = earliestRatedDate?.let { ChronoUnit.DAYS.between(it, today) + 1 } ?: 0L
    val showThirty = historySpanDays >= HASHTAG_MIN_HISTORY_DAYS_FOR_30
    val showNinety = historySpanDays >= HASHTAG_MIN_HISTORY_DAYS_FOR_90

    val initialPreset = when {
        initialAllTime -> HashtagTrendRange.ALL
        initialRange != null -> HashtagTrendRange.CUSTOM
        else -> HashtagTrendRange.SEVEN_DAYS
    }
    var presetName by rememberSaveable(normalizedTag) { mutableStateOf(initialPreset.name) }
    var customStartEpochDay by rememberSaveable(normalizedTag) {
        mutableStateOf((initialRange?.start ?: today.minusDays(29)).toEpochDay())
    }
    var customEndEpochDay by rememberSaveable(normalizedTag) {
        mutableStateOf((initialRange?.end ?: today).toEpochDay())
    }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val preset = HashtagTrendRange.valueOf(presetName)
    val customRange = HashtagDateRange(
        LocalDate.ofEpochDay(customStartEpochDay),
        LocalDate.ofEpochDay(customEndEpochDay)
    )

    LaunchedEffect(showThirty, showNinety, presetName) {
        when {
            preset == HashtagTrendRange.NINETY_DAYS && !showNinety -> {
                presetName = if (showThirty) HashtagTrendRange.THIRTY_DAYS.name else HashtagTrendRange.SEVEN_DAYS.name
            }
            preset == HashtagTrendRange.THIRTY_DAYS && !showThirty -> {
                presetName = HashtagTrendRange.SEVEN_DAYS.name
            }
        }
    }

    val range = remember(preset, customRange, today, earliestRatedDate) {
        when (preset) {
            HashtagTrendRange.SEVEN_DAYS -> HashtagDateRange(today.minusDays(6), today)
            HashtagTrendRange.THIRTY_DAYS -> HashtagDateRange(today.minusDays(29), today)
            HashtagTrendRange.NINETY_DAYS -> HashtagDateRange(today.minusDays(89), today)
            HashtagTrendRange.ALL -> HashtagDateRange(earliestRatedDate ?: today, today)
            HashtagTrendRange.CUSTOM -> customRange
        }
    }

    if (showDateRangePicker) {
        HashtagDateRangeDialog(
            initialRange = customRange,
            onDismiss = { showDateRangePicker = false },
            onConfirm = { selected ->
                customStartEpochDay = selected.start.toEpochDay()
                customEndEpochDay = selected.end.toEpochDay()
                presetName = HashtagTrendRange.CUSTOM.name
                showDateRangePicker = false
            }
        )
    }

    val entriesInRange = remember(ratedTaggedEntries, range, zoneId) {
        ratedTaggedEntries.filter { entry ->
            val date = Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
            !date.isBefore(range.start) && !date.isAfter(range.end)
        }.sortedBy { it.timestamp }
    }

    val chartStart = remember(entriesInRange, range, preset, zoneId) {
        if (
            entriesInRange.isNotEmpty() &&
            preset in setOf(
                HashtagTrendRange.THIRTY_DAYS,
                HashtagTrendRange.NINETY_DAYS,
                HashtagTrendRange.CUSTOM,
                HashtagTrendRange.ALL
            )
        ) {
            Instant.ofEpochMilli(entriesInRange.first().timestamp).atZone(zoneId).toLocalDate()
        } else {
            range.start
        }
    }
    val chartEnd = range.end.coerceAtLeast(chartStart)
    val dayCount = (ChronoUnit.DAYS.between(chartStart, chartEnd) + 1).toInt().coerceAtLeast(1)
    val trendWindowDays = MoodAnalytics.adaptiveTrendWindowDays(dayCount)
    val dailySeries = remember(entriesInRange, chartStart, chartEnd, zoneId) {
        MoodAnalytics.dailySeries(entriesInRange, chartStart, chartEnd, zoneId)
    }
    val trendValues = remember(dailySeries, trendWindowDays) {
        MoodAnalytics.dailyTrendValues(dailySeries, trendWindowDays)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(normalizedTag, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Mood trend",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close hashtag trend")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HashtagTrendRange.entries
                .filter { option ->
                    when (option) {
                        HashtagTrendRange.THIRTY_DAYS -> showThirty
                        HashtagTrendRange.NINETY_DAYS -> showNinety
                        else -> true
                    }
                }
                .forEach { option ->
                    FilterChip(
                        selected = preset == option,
                        onClick = {
                            if (option == HashtagTrendRange.CUSTOM) {
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
            formatHashtagRange(range.start, range.end),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HashtagMoodTrendChart(
                    entries = entriesInRange,
                    dailySeries = dailySeries,
                    trendValues = trendValues,
                    startDate = chartStart,
                    endDate = chartEnd,
                    zoneId = zoneId
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$trendWindowDays-day trend", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Individual rating", style = MaterialTheme.typography.labelSmall)
                }

                Text(
                    "Based on ${entriesInRange.size} ${if (entriesInRange.size == 1) "rated entry" else "rated entries"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val average = entriesInRange.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()
                val df = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                ChartExportActions(
                    ShareableChart(
                        title = "Mood trend", contextLabel = normalizedTag, startDate = chartStart, endDate = chartEnd,
                        checkInCount = entriesInRange.size, averageRating = average,
                        values = dailySeries.map { it.average }, trendValues = trendValues,
                        xLabels = listOf(chartStart.format(df), chartStart.plusDays((ChronoUnit.DAYS.between(chartStart, chartEnd))/2).format(df), chartEnd.format(df)),
                        trendLabel = "$trendWindowDays-day trend"
                    )
                )
            }
        }

        Text(
            "Only entries tagged $normalizedTag are shown. Unrated entries remain in the hashtag history but are not plotted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HashtagMoodTrendChart(
    entries: List<MoodEntry>,
    dailySeries: List<MoodAnalytics.DailyPoint>,
    trendValues: List<Double?>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId
) {
    if (entries.isEmpty()) {
        Text(
            "No rated entries with this hashtag in this period.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val pointColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    val trendColor = MaterialTheme.colorScheme.primary
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
    val days = (ChronoUnit.DAYS.between(startDate, endDate) + 1).coerceAtLeast(1)
    val middleDate = startDate.plusDays((days - 1) / 2)

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
        ) {
            val top = 8f
            val bottom = size.height - 8f
            val chartHeight = bottom - top
            val startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endMillis = endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
            val spanMillis = (endMillis - startMillis).coerceAtLeast(1L).toDouble()

            fun yFor(value: Double): Float {
                val clipped = value.coerceIn(1.0, 5.0)
                return bottom - (((clipped - 1.0) / 4.0).toFloat() * chartHeight)
            }

            fun xFor(timestamp: Long): Float {
                val fraction = ((timestamp - startMillis).toDouble() / spanMillis).coerceIn(0.0, 1.0)
                return (size.width * fraction).toFloat()
            }

            for (mood in 1..5) {
                val y = yFor(mood.toDouble())
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val trendPath = Path()
            var trendStarted = false
            dailySeries.forEachIndexed { index, point ->
                val value = trendValues.getOrNull(index) ?: return@forEachIndexed
                val timestamp = point.date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
                val x = xFor(timestamp)
                val y = yFor(value)
                if (!trendStarted) {
                    trendPath.moveTo(x, y)
                    trendStarted = true
                } else {
                    trendPath.lineTo(x, y)
                }
            }
            if (trendStarted) {
                drawPath(
                    path = trendPath,
                    color = trendColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            val pointRadius = if (entries.size <= 31) 3.5.dp.toPx() else 2.5.dp.toPx()
            entries.forEach { entry ->
                val rating = entry.rating ?: return@forEach
                drawCircle(
                    color = pointColor,
                    radius = pointRadius,
                    center = Offset(xFor(entry.timestamp), yFor(rating.toDouble()))
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
        listOf(startDate, middleDate, endDate).forEach { date ->
            Text(
                date.format(dateFormatter),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HashtagDateRangeDialog(
    initialRange: HashtagDateRange,
    onDismiss: () -> Unit,
    onConfirm: (HashtagDateRange) -> Unit
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
                    onConfirm(HashtagDateRange(start, end))
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

private fun formatHashtagRange(start: LocalDate, end: LocalDate): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return "${start.format(formatter)} – ${end.format(formatter)}"
}
