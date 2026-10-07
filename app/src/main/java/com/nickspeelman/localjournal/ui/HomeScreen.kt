package com.nickspeelman.localjournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.analytics.MoodAnalytics
import java.util.Locale

@Composable
fun HomeScreen(
    summary: MoodAnalytics.HomeSummary,
    entryCount: Int,
    onHashtagClick: (String) -> Unit = {},
    onViewAllHashtags: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Mood Home",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        if (entryCount == 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("No check-ins yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Your recent mood summaries will appear here after you start checking in.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(vertical = 18.dp, horizontal = 10.dp)) {
                Text(
                    text = "Mood averages",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, bottom = 14.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    MoodMetric(
                        label = "30 DAYS",
                        metric = summary.last30Days,
                        modifier = Modifier.weight(1f)
                    )
                    MoodMetric(
                        label = "TODAY",
                        metric = summary.today,
                        modifier = Modifier.weight(1f)
                    )
                    MoodMetric(
                        label = "7 DAYS",
                        metric = summary.last7Days,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Top Hashtags",
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = onViewAllHashtags) {
                Text("View all hashtags")
            }
        }

        if (summary.topHashtags.isEmpty()) {
            Text(
                text = "No hashtags recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Start)
            )
        } else {
            summary.topHashtags.forEach { stat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHashtagClick(stat.tag) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stat.tag,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = buildString {
                            append("${stat.entryCount} ${if (stat.entryCount == 1) "entry" else "entries"}")
                            append(" · ")
                            append(stat.averageRating?.let { "${formatMood(it)} avg" } ?: "No rated entries")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "View ${stat.tag} history",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodMetric(
    label: String,
    metric: MoodAnalytics.ComparisonMetric,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = metric.current?.let(::formatMood) ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (metric.previous != null && metric.current != null) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = when (metric.direction) {
                        MoodAnalytics.TrendDirection.UP -> Icons.Default.ArrowUpward
                        MoodAnalytics.TrendDirection.DOWN -> Icons.Default.ArrowDownward
                        else -> Icons.Default.ArrowForward
                    },
                    contentDescription = when (metric.direction) {
                        MoodAnalytics.TrendDirection.UP -> "up from comparison period"
                        MoodAnalytics.TrendDirection.DOWN -> "down from comparison period"
                        else -> "unchanged from comparison period"
                    },
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Text(
            text = when {
                metric.current == null -> if (label == "TODAY") "No check-ins today" else "No check-ins in this period"
                metric.previous == null -> "Not enough earlier data to compare yet"
                else -> "from ${formatMood(metric.previous)}"
            },
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
        )
    }
}

private fun formatMood(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

@Composable
fun AddMoodEntryDialog(
    rating: Int?,
    input: String,
    onRatingChange: (Int?) -> Unit,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you feeling?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Choose a rating, then add an optional note or #tags.")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..5).forEach { value ->
                        if (rating == value) {
                            FilledTonalButton(
                                onClick = { onRatingChange(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = when (value) {
                                            1 -> "Mood rating 1 of 5, worst"
                                            5 -> "Mood rating 5 of 5, best"
                                            else -> "Mood rating $value of 5"
                                        }
                                    },
                                contentPadding = PaddingValues(horizontal = 0.dp)
                            ) {
                                Text(value.toString())
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onRatingChange(value) },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = when (value) {
                                            1 -> "Mood rating 1 of 5, worst"
                                            5 -> "Mood rating 5 of 5, best"
                                            else -> "Mood rating $value of 5"
                                        }
                                    },
                                contentPadding = PaddingValues(horizontal = 0.dp)
                            ) {
                                Text(value.toString())
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 · worst", style = MaterialTheme.typography.labelSmall)
                    Text("5 · best", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional note or #tags") },
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = rating != null || input.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun AddNoteToSavedEntryDialog(
    rating: Int,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "$rating / 5 saved",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Add an optional note or #tags to this check-in.")
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Optional note or #tags") },
                    minLines = 2,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = input.isNotBlank()
            ) {
                Text("Save note")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
