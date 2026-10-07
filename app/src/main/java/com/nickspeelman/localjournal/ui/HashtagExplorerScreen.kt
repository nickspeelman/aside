package com.nickspeelman.localjournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.analytics.HashtagAnalytics
import com.nickspeelman.localjournal.analytics.HashtagDateRange
import com.nickspeelman.localjournal.analytics.HashtagSort
import com.nickspeelman.localjournal.data.MoodEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ExplorerRange(val label: String) {
    ALL("All time"),
    THIRTY_DAYS("Last 30 days"),
    NINETY_DAYS("Last 90 days"),
    CUSTOM("Custom")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagExplorerScreen(
    entries: List<MoodEntry>,
    onClose: () -> Unit,
    onHashtagClick: (String, HashtagDateRange?) -> Unit
) {
    BackHandler(onBack = onClose)
    val zoneId = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zoneId)
    val locale = Locale.getDefault()
    val longDayFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM d", locale) }
    val shortDayFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM d", locale) }

    var query by rememberSaveable { mutableStateOf("") }
    var rangeName by rememberSaveable { mutableStateOf(ExplorerRange.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(HashtagSort.MOST_USED.name) }
    var customStartEpochDay by rememberSaveable { mutableStateOf(today.minusDays(29).toEpochDay()) }
    var customEndEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var showCustomRangePicker by remember { mutableStateOf(false) }
    var rangeMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val selectedRange = ExplorerRange.valueOf(rangeName)
    val selectedSort = HashtagSort.valueOf(sortName)
    val customRange = HashtagDateRange(
        LocalDate.ofEpochDay(customStartEpochDay),
        LocalDate.ofEpochDay(customEndEpochDay)
    )
    val activeRange = remember(selectedRange, customRange, today) {
        when (selectedRange) {
            ExplorerRange.ALL -> null
            ExplorerRange.THIRTY_DAYS -> HashtagDateRange(today.minusDays(29), today)
            ExplorerRange.NINETY_DAYS -> HashtagDateRange(today.minusDays(89), today)
            ExplorerRange.CUSTOM -> customRange
        }
    }

    val snapshot = remember(entries, activeRange, zoneId) {
        HashtagAnalytics.snapshot(entries, activeRange, zoneId)
    }
    val visibleStats = remember(snapshot.stats, query, selectedSort) {
        HashtagAnalytics.filterAndSort(snapshot.stats, query, selectedSort)
    }

    if (showCustomRangePicker) {
        ExplorerDateRangeDialog(
            initialRange = customRange,
            onDismiss = { showCustomRangePicker = false },
            onConfirm = { range ->
                customStartEpochDay = range.start.toEpochDay()
                customEndEpochDay = range.end.toEpochDay()
                rangeName = ExplorerRange.CUSTOM.name
                showCustomRangePicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hashtags", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${snapshot.stats.size} ${if (snapshot.stats.size == 1) "hashtag" else "hashtags"} across " +
                        "${snapshot.taggedEntryCount} ${if (snapshot.taggedEntryCount == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close hashtags")
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search hashtags") }
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = rangeMenuExpanded,
                onExpandedChange = { rangeMenuExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedRange.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Time range") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rangeMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .semantics { contentDescription = "Time range, ${selectedRange.label}" }
                )
                ExposedDropdownMenu(
                    expanded = rangeMenuExpanded,
                    onDismissRequest = { rangeMenuExpanded = false }
                ) {
                    ExplorerRange.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                rangeMenuExpanded = false
                                if (option == ExplorerRange.CUSTOM) {
                                    showCustomRangePicker = true
                                } else {
                                    rangeName = option.name
                                }
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = sortMenuExpanded,
                onExpandedChange = { sortMenuExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedSort.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sort") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .semantics { contentDescription = "Sort, ${selectedSort.label}" }
                )
                ExposedDropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    HashtagSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                sortName = option.name
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (activeRange != null) {
            Text(
                text = formatExplorerRange(activeRange),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        val emptyTitle = when {
            entries.none { it.hashtags.isNotBlank() } -> "No hashtags yet"
            query.isNotBlank() && visibleStats.isEmpty() -> "No matching hashtags"
            snapshot.stats.isEmpty() -> "No hashtags in this period"
            else -> null
        }

        if (emptyTitle != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(emptyTitle, style = MaterialTheme.typography.titleMedium)
                    if (emptyTitle == "No hashtags yet") {
                        Spacer(Modifier.height(4.dp))
                        Text("Hashtags you use in journal notes will appear here.")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visibleStats, key = { it.tag }) { stat ->
                    val lastUsed = remember(stat.lastUsedTimestamp, zoneId) {
                        Instant.ofEpochMilli(stat.lastUsedTimestamp).atZone(zoneId).toLocalDate()
                    }
                    val semantics = buildString {
                        append(stat.tag)
                        append(", ${stat.entryCount} ${if (stat.entryCount == 1) "entry" else "entries"}")
                        append(stat.averageRating?.let { ", average rating ${formatExplorerMood(it)}" } ?: ", no rated entries")
                        append(", last used ${lastUsed.format(longDayFormatter)}")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHashtagClick(stat.tag, activeRange) }
                            .semantics { contentDescription = semantics }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stat.tag,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                buildString {
                                    append("${stat.entryCount} ${if (stat.entryCount == 1) "entry" else "entries"} · ")
                                    append(stat.averageRating?.let { "Average ${formatExplorerMood(it)}" } ?: "No rated entries")
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Last used ${lastUsed.format(shortDayFormatter)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplorerDateRangeDialog(
    initialRange: HashtagDateRange,
    onDismiss: () -> Unit,
    onConfirm: (HashtagDateRange) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = initialRange.end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: return@TextButton
                    onConfirm(
                        HashtagDateRange(
                            Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate(),
                            Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    )
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state, title = { Text("Select date range", modifier = Modifier.padding(16.dp)) })
    }
}

private fun formatExplorerMood(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

private fun formatExplorerRange(range: HashtagDateRange): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return "${range.start.format(formatter)} – ${range.end.format(formatter)}"
}
