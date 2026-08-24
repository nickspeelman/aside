package com.nickspeelman.localjournal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.data.UserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onSettingsChanged: (UserSettings) -> Unit
) {
    var promptsPerDay by remember(settings) { mutableIntStateOf(settings.promptsPerDay) }
    var sleepStartHour by remember(settings) { mutableIntStateOf(settings.sleepStartHour) }
    var sleepStartMinute by remember(settings) { mutableIntStateOf(settings.sleepStartMinute) }
    var sleepEndHour by remember(settings) { mutableIntStateOf(settings.sleepEndHour) }
    var sleepEndMinute by remember(settings) { mutableIntStateOf(settings.sleepEndMinute) }
    var isPaused by remember(settings) { mutableStateOf(settings.isPaused) }

    var expanded by remember { mutableStateOf(false) }

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
                        modifier = Modifier.menuAnchor().fillMaxWidth()
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
                TimeInputRow(
                    hour = sleepStartHour,
                    minute = sleepStartMinute,
                    onTimeChanged = { h, m -> sleepStartHour = h; sleepStartMinute = m }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(text = "Wake up", style = MaterialTheme.typography.bodySmall)
                TimeInputRow(
                    hour = sleepEndHour,
                    minute = sleepEndMinute,
                    onTimeChanged = { h, m -> sleepEndHour = h; sleepEndMinute = m }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isPaused,
                        onCheckedChange = { isPaused = it }
                    )
                    Text(text = "Take a break", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "When enabled, the app will stop sending mood prompts until you uncheck this box.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 48.dp)
                )
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
                        isPaused = isPaused
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Apply")
        }
    }
}

@Composable
fun TimeInputRow(hour: Int, minute: Int, onTimeChanged: (Int, Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = hour.toString().padStart(2, '0'),
            onValueChange = { val h = it.toIntOrNull() ?: 0; onTimeChanged(h.coerceIn(0, 23), minute) },
            modifier = Modifier.width(64.dp),
            label = { Text("HH") }
        )
        Text(text = ":", modifier = Modifier.padding(horizontal = 8.dp))
        TextField(
            value = minute.toString().padStart(2, '0'),
            onValueChange = { val m = it.toIntOrNull() ?: 0; onTimeChanged(hour, m.coerceIn(0, 59)) },
            modifier = Modifier.width(64.dp),
            label = { Text("MM") }
        )
    }
}
