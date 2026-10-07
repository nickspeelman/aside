package com.nickspeelman.localjournal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.data.HashtagUtils
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.MoodInputParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryDialog(
    entry: MoodEntry,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (MoodEntry) -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val originalDateTime = remember(entry.id, entry.timestamp) {
        Instant.ofEpochMilli(entry.timestamp).atZone(zoneId)
    }

    var rating by remember(entry.id) { mutableStateOf(entry.rating) }
    var content by remember(entry.id) { mutableStateOf(contentForEditing(entry)) }
    var selectedDate by remember(entry.id) { mutableStateOf(originalDateTime.toLocalDate()) }
    var selectedTime by remember(entry.id) { mutableStateOf(originalDateTime.toLocalTime()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = use24Hour,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                // Choosing a new clock time intentionally sets seconds/fractions to zero. If the
                // user never opens the time picker, the original timestamp precision is retained.
                selectedTime = LocalTime.of(hour, minute)
                showTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Mood rating")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..5).forEach { value ->
                        val selected = rating == value
                        if (selected) {
                            Button(
                                onClick = { rating = value },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = when (value) {
                                            1 -> "Mood rating 1 of 5, worst"
                                            5 -> "Mood rating 5 of 5, best"
                                            else -> "Mood rating $value of 5"
                                        }
                                    }
                            ) { Text(value.toString()) }
                        } else {
                            OutlinedButton(
                                onClick = { rating = value },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = when (value) {
                                            1 -> "Mood rating 1 of 5, worst"
                                            5 -> "Mood rating 5 of 5, best"
                                            else -> "Mood rating $value of 5"
                                        }
                                    }
                            ) { Text(value.toString()) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 · worst", style = MaterialTheme.typography.labelSmall)
                    Text("5 · best", style = MaterialTheme.typography.labelSmall)
                }
                if (rating == null) {
                    Text("No rating — this entry won't affect mood calculations.")
                } else {
                    TextButton(onClick = { rating = null }) { Text("Remove rating") }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note") },
                    placeholder = { Text("Optional note or #tags") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
                        Text(selectedTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val timestamp = selectedDate
                        .atTime(selectedTime)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli()
                    val parsed = MoodInputParser.parseContent(rating, content)
                    onSave(
                        entry.copy(
                            rating = parsed.rating,
                            note = parsed.note,
                            hashtags = parsed.hashtags,
                            timestamp = timestamp
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun contentForEditing(entry: MoodEntry): String {
    val note = entry.note.trim()
    val tagsAlreadyInNote = HashtagUtils.extractFromContent(note).toSet()
    val missingTags = HashtagUtils.parse(entry.hashtags).filterNot { it in tagsAlreadyInNote }
    return listOf(note, missingTags.joinToString(" "))
        .filter { it.isNotBlank() }
        .joinToString(" ")
}
