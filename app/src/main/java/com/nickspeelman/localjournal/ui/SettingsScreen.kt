package com.nickspeelman.localjournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nickspeelman.localjournal.data.UserSettings
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    notificationsEnabled: Boolean,
    onSendTestNotification: () -> Unit,
    onSettingsChanged: (UserSettings) -> Unit
) {
    var promptsPerDay by remember(settings) { mutableIntStateOf(settings.promptsPerDay) }
    var sleepStartHour by remember(settings) { mutableIntStateOf(settings.sleepStartHour) }
    var sleepStartMinute by remember(settings) { mutableIntStateOf(settings.sleepStartMinute) }
    var sleepEndHour by remember(settings) { mutableIntStateOf(settings.sleepEndHour) }
    var sleepEndMinute by remember(settings) { mutableIntStateOf(settings.sleepEndMinute) }
    var isPaused by remember(settings) { mutableStateOf(settings.isPaused) }
    var use24Hour by remember(settings) { mutableStateOf(settings.use24Hour) }

    var expanded by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        TimePickerDialog(
            initialHour = sleepStartHour,
            initialMinute = sleepStartMinute,
            is24Hour = use24Hour,
            onDismiss = { showStartPicker = false },
            onConfirm = { h, m ->
                sleepStartHour = h
                sleepStartMinute = m
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        TimePickerDialog(
            initialHour = sleepEndHour,
            initialMinute = sleepEndMinute,
            is24Hour = use24Hour,
            onDismiss = { showEndPicker = false },
            onConfirm = { h, m ->
                sleepEndHour = h
                sleepEndMinute = m
                showEndPicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Common", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Use 24-hour clock", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = use24Hour, onCheckedChange = { use24Hour = it })
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Frequency", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = "$promptsPerDay prompts per day",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        (1..6).forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count prompts per day") },
                                onClick = {
                                    promptsPerDay = count
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Sleep Schedule", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Bedtime", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = formatTime(sleepStartHour, sleepStartMinute, use24Hour))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Wake up", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = formatTime(sleepEndHour, sleepEndMinute, use24Hour))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPaused, onCheckedChange = { isPaused = it })
                    Text(text = "Take a break", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "When enabled, the app will stop sending mood prompts until you uncheck this box.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 48.dp)
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Debug", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (notificationsEnabled) {
                        "Notifications are enabled."
                    } else {
                        "Notifications are blocked or not granted."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSendTestNotification,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send test notification now")
                }
            }
        }

        Button(
            onClick = {
                onSettingsChanged(
                    UserSettings(
                        promptsPerDay = promptsPerDay,
                        sleepStartHour = sleepStartHour,
                        sleepStartMinute = sleepStartMinute,
                        sleepEndHour = sleepEndHour,
                        sleepEndMinute = sleepEndMinute,
                        isPaused = isPaused,
                        use24Hour = use24Hour
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text("Save & Apply")
        }
    }
}

private fun formatTime(hour: Int, minute: Int, use24Hour: Boolean): String {
    return if (use24Hour) {
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    } else {
        val amPm = if (hour < 12) "AM" else "PM"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        String.format(Locale.getDefault(), "%d:%02d %s", h, minute, amPm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
                .background(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = "Select Time",
                    style = MaterialTheme.typography.labelMedium
                )
                
                CustomTimePicker(
                    initialHour = initialHour,
                    initialMinute = initialMinute,
                    is24Hour = is24Hour,
                    onTimeChange = { h, m ->
                        selectedHour = h
                        selectedMinute = m
                    }
                )

                Row(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            onConfirm(selectedHour, selectedMinute)
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
