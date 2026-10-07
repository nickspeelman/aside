package com.nickspeelman.localjournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.analytics.HashtagDateRange
import com.nickspeelman.localjournal.data.HashtagUtils
import com.nickspeelman.localjournal.data.MoodEntry
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

sealed interface HistoryFilter {
    data class Date(val date: LocalDate) : HistoryFilter
    data class WeekdayRange(
        val dayOfWeek: DayOfWeek,
        val startDate: LocalDate,
        val endDate: LocalDate
    ) : HistoryFilter

    data class Hashtag(val tag: String, val range: HashtagDateRange? = null, val allTime: Boolean = false) : HistoryFilter
}

@Composable
fun HistoryScreen(
    entries: List<MoodEntry>,
    filter: HistoryFilter? = null,
    onCloseFilter: (() -> Unit)? = null,
    onViewHashtagTrend: ((String) -> Unit)? = null,
    onEditEntry: (MoodEntry) -> Unit,
    onDeleteEntry: (MoodEntry) -> Unit
) {
    if (filter != null && onCloseFilter != null) {
        BackHandler(onBack = onCloseFilter)
    }

    val zoneId = ZoneId.systemDefault()
    val filteredEntries = remember(entries, filter, zoneId) {
        when (filter) {
            null -> entries
            is HistoryFilter.Date -> entries.filter { entry ->
                Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate() == filter.date
            }
            is HistoryFilter.WeekdayRange -> entries.filter { entry ->
                val date = Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
                !date.isBefore(filter.startDate) &&
                    !date.isAfter(filter.endDate) &&
                    date.dayOfWeek == filter.dayOfWeek
            }
            is HistoryFilter.Hashtag -> entries.filter { entry ->
                if (!HashtagUtils.contains(entry.hashtags, filter.tag)) return@filter false
                val range = filter.range ?: return@filter true
                val date = Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
                !date.isBefore(range.start) && !date.isAfter(range.end)
            }
        }
    }

    val locale = Locale.getDefault()
    val fullDateFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM d, yyyy", locale) }
    val shortDateFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM d, yyyy", locale) }
    val title = when (filter) {
        null -> "Mood History"
        is HistoryFilter.Date -> filter.date.format(fullDateFormatter)
        is HistoryFilter.WeekdayRange -> {
            val dayName = filter.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
            "$dayName entries"
        }
        is HistoryFilter.Hashtag -> HashtagUtils.normalize(filter.tag)
    }
    val subtitle = when (filter) {
        is HistoryFilter.WeekdayRange ->
            "${filter.startDate.format(shortDateFormatter)} – ${filter.endDate.format(shortDateFormatter)}"
        is HistoryFilter.Hashtag -> buildString {
            append("${filteredEntries.size} ${if (filteredEntries.size == 1) "entry" else "entries"}")
            filter.range?.let { range ->
                append(" · ${range.start.format(shortDateFormatter)} – ${range.end.format(shortDateFormatter)}")
            }
        }
        else -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = MaterialTheme.typography.headlineMedium)
                        subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (filter is HistoryFilter.Hashtag && onViewHashtagTrend != null) {
                            TextButton(onClick = { onViewHashtagTrend(filter.tag) }) {
                                Icon(
                                    Icons.Default.BarChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("View trend")
                            }
                        }
                        if (filter != null && onCloseFilter != null) {
                            IconButton(onClick = onCloseFilter) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = if (filter is HistoryFilter.Hashtag) {
                                        "Close hashtag history"
                                    } else {
                                        "Close and return to report"
                                    }
                                )
                            }
                        }
                    }
                }
                if (filter == null) Spacer(modifier = Modifier.height(8.dp))
            }
        }
        items(filteredEntries, key = { it.id }) { entry ->
            MoodEntryItem(
                entry = entry,
                onEdit = { onEditEntry(entry) },
                onDelete = { onDeleteEntry(entry) }
            )
        }
        if (filteredEntries.isEmpty()) {
            item {
                Text(
                    text = when {
                        entries.isEmpty() && filter == null -> "No check-ins yet."
                        filter is HistoryFilter.Hashtag -> "No entries tagged ${HashtagUtils.normalize(filter.tag)} in this period."
                        else -> "No check-ins for this selection."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MoodEntryItem(
    entry: MoodEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val formatter = remember(locale) { SimpleDateFormat("MMM dd, HH:mm", locale) }

    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.rating?.let { "Rating: $it/5" } ?: "No rating",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatter.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Entry actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit entry") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete entry") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            val displayContent = historyDisplayContent(entry)
            if (displayContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = displayContent, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun historyDisplayContent(entry: MoodEntry): String {
    val note = entry.note.trim()
    val tagsAlreadyInNote = HashtagUtils.extractFromContent(note).toSet()
    val missingTags = HashtagUtils.parse(entry.hashtags).filterNot { it in tagsAlreadyInNote }

    // New entries retain hashtags in context inside note. Older entries were saved with hashtags
    // stripped out, so their exact original positions cannot be recovered; append only those
    // legacy tags that are not already present rather than showing a separate hashtag row.
    return listOf(note, missingTags.joinToString(" "))
        .filter { it.isNotBlank() }
        .joinToString(" ")
}
