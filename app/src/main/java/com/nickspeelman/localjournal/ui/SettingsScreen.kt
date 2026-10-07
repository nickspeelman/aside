package com.nickspeelman.localjournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.os.Build
import android.content.pm.ApplicationInfo
import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.data.ManualBackupState
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    notificationsEnabled: Boolean,
    entryCount: Int,
    backupState: ManualBackupState,
    androidBackupPolicy: AndroidBackupPolicy,
    canRevealJournalMetadata: Boolean,
    privacyStatusTitle: String,
    privacyStatusSubtitle: String?,
    onOpenPrivacy: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onExportJournal: () -> Unit,
    onBackupJournal: () -> Unit,
    onBackupReminderChanged: (Boolean, Int) -> Unit,
    onRestoreBackup: () -> Unit,
    onDeleteAllJournalData: () -> Unit,
    onSendTestNotification: (Long) -> Unit,
    onSendTestWeeklyReport: (Long) -> Unit,
    onSendTestMonthlyReport: (Long) -> Unit,
    onBetaFeedback: () -> Unit,
    onSettingsChanged: (UserSettings) -> Unit
) {
    val context = LocalContext.current
    val isDebuggable = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    val appVersionLabel = remember(context) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        "$versionName ($versionCode)"
    }

    var promptsPerDay by remember(settings) { mutableIntStateOf(settings.promptsPerDay) }
    var sleepStartHour by remember(settings) { mutableIntStateOf(settings.sleepStartHour) }
    var sleepStartMinute by remember(settings) { mutableIntStateOf(settings.sleepStartMinute) }
    var sleepEndHour by remember(settings) { mutableIntStateOf(settings.sleepEndHour) }
    var sleepEndMinute by remember(settings) { mutableIntStateOf(settings.sleepEndMinute) }
    var isPaused by remember(settings) { mutableStateOf(settings.isPaused) }
    var checkInNotificationTimeoutMinutes by remember(settings) {
        mutableIntStateOf(settings.checkInNotificationTimeoutMinutes)
    }
    var use24Hour by remember(settings) { mutableStateOf(settings.use24Hour) }
    var weeklyReportEnabled by remember(settings) { mutableStateOf(settings.weeklyReportEnabled) }
    var weeklyReportDay by remember(settings) { mutableIntStateOf(settings.weeklyReportDay) }
    var weeklyReportHour by remember(settings) { mutableIntStateOf(settings.weeklyReportHour) }
    var weeklyReportMinute by remember(settings) { mutableIntStateOf(settings.weeklyReportMinute) }
    var weeklyReportUseBedtimeOffset by remember(settings) {
        mutableStateOf(settings.weeklyReportUseBedtimeOffset)
    }
    var monthlyReportEnabled by remember(settings) { mutableStateOf(settings.monthlyReportEnabled) }
    var monthlyReportReplaceWeekly by remember(settings) { mutableStateOf(settings.monthlyReportReplaceWeekly) }
    var monthlyReportHour by remember(settings) { mutableIntStateOf(settings.monthlyReportHour) }
    var monthlyReportMinute by remember(settings) { mutableIntStateOf(settings.monthlyReportMinute) }
    var monthlyReportUseBedtimeOffset by remember(settings) {
        mutableStateOf(settings.monthlyReportUseBedtimeOffset)
    }

    var expanded by remember { mutableStateOf(false) }
    var timeoutExpanded by remember { mutableStateOf(false) }
    var weeklyDayExpanded by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showWeeklyReportTimePicker by remember { mutableStateOf(false) }
    var showMonthlyReportTimePicker by remember { mutableStateOf(false) }
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var testDelaySeconds by remember { mutableLongStateOf(10L) }

    fun currentSettings() = settings.copy(
        promptsPerDay = promptsPerDay,
        sleepStartHour = sleepStartHour,
        sleepStartMinute = sleepStartMinute,
        sleepEndHour = sleepEndHour,
        sleepEndMinute = sleepEndMinute,
        isPaused = isPaused,
        checkInNotificationTimeoutMinutes = checkInNotificationTimeoutMinutes,
        use24Hour = use24Hour,
        weeklyReportEnabled = weeklyReportEnabled,
        weeklyReportDay = weeklyReportDay,
        weeklyReportHour = weeklyReportHour,
        weeklyReportMinute = weeklyReportMinute,
        weeklyReportUseBedtimeOffset = weeklyReportUseBedtimeOffset,
        monthlyReportEnabled = monthlyReportEnabled,
        monthlyReportReplaceWeekly = monthlyReportReplaceWeekly,
        monthlyReportHour = monthlyReportHour,
        monthlyReportMinute = monthlyReportMinute,
        monthlyReportUseBedtimeOffset = monthlyReportUseBedtimeOffset
    )

    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }

    fun persistCurrentSettings() {
        onSettingsChanged(currentSettings())
    }

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
                persistCurrentSettings()
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
                persistCurrentSettings()
            }
        )
    }

    if (showWeeklyReportTimePicker) {
        val initialTime = if (weeklyReportUseBedtimeOffset) {
            LocalTime.of(sleepStartHour, sleepStartMinute).minusHours(2)
        } else {
            LocalTime.of(weeklyReportHour, weeklyReportMinute)
        }
        TimePickerDialog(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = use24Hour,
            onDismiss = { showWeeklyReportTimePicker = false },
            onConfirm = { h, m ->
                weeklyReportHour = h
                weeklyReportMinute = m
                weeklyReportUseBedtimeOffset = false
                showWeeklyReportTimePicker = false
                persistCurrentSettings()
            }
        )
    }

    if (showMonthlyReportTimePicker) {
        val initialTime = if (monthlyReportUseBedtimeOffset) {
            LocalTime.of(sleepStartHour, sleepStartMinute).minusHours(2)
        } else {
            LocalTime.of(monthlyReportHour, monthlyReportMinute)
        }
        TimePickerDialog(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = use24Hour,
            onDismiss = { showMonthlyReportTimePicker = false },
            onConfirm = { h, m ->
                monthlyReportHour = h
                monthlyReportMinute = m
                monthlyReportUseBedtimeOffset = false
                showMonthlyReportTimePicker = false
                persistCurrentSettings()
            }
        )
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = { Text("Delete all journal data?") },
            text = {
                Text(
                    if (!canRevealJournalMetadata) {
                        "This will permanently delete your entire journal. Your app settings will not change. This cannot be undone."
                    } else if (entryCount == 1) {
                        "This will permanently delete 1 journal entry. Your app settings will not change. This cannot be undone."
                    } else {
                        "This will permanently delete $entryCount journal entries. Your app settings will not change. This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirmation = false
                        onDeleteAllJournalData()
                    },
                    enabled = entryCount > 0
                ) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (settingsSection != null) {
        BackHandler { settingsSection = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (settingsSection == null) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

            SettingsMenuCard(
                title = "Check-ins",
                subtitle = "$promptsPerDay per day · ${formatTime(sleepEndHour, sleepEndMinute, use24Hour)}–${formatTime(sleepStartHour, sleepStartMinute, use24Hour)}",
                onClick = { settingsSection = SettingsSection.CHECK_INS }
            )
            SettingsMenuCard(
                title = "Reports",
                subtitle = when {
                    weeklyReportEnabled && monthlyReportEnabled -> "Weekly and monthly reports"
                    weeklyReportEnabled -> "Weekly reports"
                    monthlyReportEnabled -> "Monthly reports"
                    else -> "Reports off"
                },
                onClick = { settingsSection = SettingsSection.REPORTS }
            )
            SettingsMenuCard(
                title = "Privacy",
                subtitle = listOfNotNull(privacyStatusTitle, privacyStatusSubtitle).joinToString(" · "),
                onClick = onOpenPrivacy
            )
            SettingsMenuCard(
                title = "Data & backup",
                subtitle = if (canRevealJournalMetadata) {
                    "$entryCount ${if (entryCount == 1) "entry" else "entries"} · backup, export, restore"
                } else {
                    "Backup, export, restore, and delete"
                },
                onClick = { settingsSection = SettingsSection.DATA }
            )
            SettingsMenuCard(
                title = "General",
                subtitle = "Time format and app preferences",
                onClick = { settingsSection = SettingsSection.GENERAL }
            )
            SettingsMenuCard(
                title = "Beta feedback",
                subtitle = "Report a problem or share an idea",
                onClick = { settingsSection = SettingsSection.FEEDBACK }
            )
            SettingsMenuCard(
                title = "Privacy policy",
                subtitle = "Public policy with revision history",
                onClick = onOpenPrivacyPolicy
            )
            if (isDebuggable) {
                SettingsMenuCard(
                    title = "Testing",
                    subtitle = "Send test check-ins and reports",
                    onClick = { settingsSection = SettingsSection.TESTING }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { settingsSection = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings")
                }
                Text(
                    text = settingsSection!!.title,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            when (settingsSection) {
                SettingsSection.CHECK_INS -> {
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
                                    persistCurrentSettings()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Unanswered check-in expires after",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = timeoutExpanded,
                    onExpandedChange = { timeoutExpanded = !timeoutExpanded }
                ) {
                    TextField(
                        value = checkInTimeoutLabel(checkInNotificationTimeoutMinutes),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeoutExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = timeoutExpanded,
                        onDismissRequest = { timeoutExpanded = false }
                    ) {
                        listOf(0, 15, 30, 60, 120, 240).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(checkInTimeoutLabel(minutes)) },
                                onClick = {
                                    checkInNotificationTimeoutMinutes = minutes
                                    timeoutExpanded = false
                                    persistCurrentSettings()
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "Opening or answering a check-in removes it immediately. If you leave it untouched, Android removes it after this much time. The timer starts when the notification is posted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
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
                    Checkbox(
                        checked = isPaused,
                        onCheckedChange = {
                            isPaused = it
                            persistCurrentSettings()
                        }
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
                }
                SettingsSection.REPORTS -> {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Weekly report", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = weeklyReportScheduleSummary(
                                day = weeklyReportDay,
                                useBedtimeOffset = weeklyReportUseBedtimeOffset,
                                reportHour = weeklyReportHour,
                                reportMinute = weeklyReportMinute,
                                bedtimeHour = sleepStartHour,
                                bedtimeMinute = sleepStartMinute,
                                wakeHour = sleepEndHour,
                                wakeMinute = sleepEndMinute,
                                use24Hour = use24Hour
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = weeklyReportEnabled,
                        onCheckedChange = {
                            weeklyReportEnabled = it
                            persistCurrentSettings()
                        }
                    )
                }

                if (weeklyReportEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Day", style = MaterialTheme.typography.bodySmall)
                    ExposedDropdownMenuBox(
                        expanded = weeklyDayExpanded,
                        onExpandedChange = { weeklyDayExpanded = !weeklyDayExpanded }
                    ) {
                        TextField(
                            value = dayName(weeklyReportDay),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = weeklyDayExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = weeklyDayExpanded,
                            onDismissRequest = { weeklyDayExpanded = false }
                        ) {
                            DayOfWeek.values().forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(dayName(day.value)) },
                                    onClick = {
                                        weeklyReportDay = day.value
                                        weeklyDayExpanded = false
                                        persistCurrentSettings()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Time", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { showWeeklyReportTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val defaultTime = LocalTime.of(sleepStartHour, sleepStartMinute).minusHours(2)
                        Text(
                            if (weeklyReportUseBedtimeOffset) {
                                "2 hours before bedtime (${formatTime(defaultTime.hour, defaultTime.minute, use24Hour)})"
                            } else {
                                formatTime(weeklyReportHour, weeklyReportMinute, use24Hour)
                            }
                        )
                    }

                    if (!weeklyReportUseBedtimeOffset) {
                        TextButton(
                            onClick = {
                                weeklyReportUseBedtimeOffset = true
                                persistCurrentSettings()
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Use 2 hours before bedtime")
                        }
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Monthly report", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = monthlyReportScheduleSummary(
                                enabled = monthlyReportEnabled,
                                weeklyEnabled = weeklyReportEnabled,
                                replaceWeekly = monthlyReportReplaceWeekly,
                                useBedtimeOffset = monthlyReportUseBedtimeOffset,
                                reportHour = monthlyReportHour,
                                reportMinute = monthlyReportMinute,
                                bedtimeHour = sleepStartHour,
                                bedtimeMinute = sleepStartMinute,
                                wakeHour = sleepEndHour,
                                wakeMinute = sleepEndMinute,
                                use24Hour = use24Hour
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = monthlyReportEnabled,
                        onCheckedChange = {
                            monthlyReportEnabled = it
                            persistCurrentSettings()
                        }
                    )
                }

                if (monthlyReportEnabled) {
                    if (weeklyReportEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Delivery", style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = monthlyReportReplaceWeekly,
                                onClick = {
                                    monthlyReportReplaceWeekly = true
                                    persistCurrentSettings()
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Replace last weekly report")
                                Text(
                                    "Uses the normal weekly report day and time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !monthlyReportReplaceWeekly,
                                onClick = {
                                    monthlyReportReplaceWeekly = false
                                    persistCurrentSettings()
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Send separately on last day")
                                Text(
                                    "Keeps the weekly report and sends a monthly report too.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!weeklyReportEnabled || !monthlyReportReplaceWeekly) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Time on last day of month", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = { showMonthlyReportTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val defaultTime = LocalTime.of(sleepStartHour, sleepStartMinute).minusHours(2)
                            Text(
                                if (monthlyReportUseBedtimeOffset) {
                                    "2 hours before bedtime (${formatTime(defaultTime.hour, defaultTime.minute, use24Hour)})"
                                } else {
                                    formatTime(monthlyReportHour, monthlyReportMinute, use24Hour)
                                }
                            )
                        }
                        if (!monthlyReportUseBedtimeOffset) {
                            TextButton(
                                onClick = {
                                    monthlyReportUseBedtimeOffset = true
                                    persistCurrentSettings()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Use 2 hours before bedtime")
                            }
                        }
                    }
                }
            }
        }
                }
                SettingsSection.DATA -> {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Data management", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (canRevealJournalMetadata) {
                        "$entryCount ${if (entryCount == 1) "journal entry" else "journal entries"} stored on this device."
                    } else {
                        "Journal data is stored on this device. Unlock the journal to reveal journal details."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Manual Aside backup", style = MaterialTheme.typography.titleSmall)
                val backupDate = backupState.lastSuccessfulBackupAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
                Text(
                    text = when {
                        !backupState.statusKnown -> "Manual backup status unavailable. Aside will track manual backups you create from now on."
                        !backupState.hasSuccessfulBackup -> "No manual backup yet. You haven’t created a portable Aside backup. Android-managed backup and device transfer are controlled separately below."
                        backupState.isCurrent -> "Last backup: $backupDate. Your journal hasn't changed since this backup."
                        backupState.entriesAddedSinceBackup > 0 && backupState.otherChangesSinceBackup -> "Last backup: $backupDate. ${backupState.entriesAddedSinceBackup} new entries and other changes since your last backup."
                        backupState.entriesAddedSinceBackup > 0 -> "Last backup: $backupDate. ${backupState.entriesAddedSinceBackup} new entries since your last backup."
                        else -> "Last backup: $backupDate. Your journal has changed since this backup."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "You choose where your backup is saved. Aside does not upload it itself. If you save it somewhere that syncs or backs up to the cloud, your journal data may leave this device and will be subject to that service’s privacy and security practices.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onBackupJournal, modifier = Modifier.fillMaxWidth()) {
                    Text(if (backupState.hasSuccessfulBackup) "Back up now" else "Create backup")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Remind me to back up")
                    Switch(checked = backupState.reminderEnabled, onCheckedChange = { onBackupReminderChanged(it, backupState.reminderIntervalDays) })
                }
                if (backupState.reminderEnabled) {
                    Text("Reminder frequency", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(7 to "Weekly", 14 to "2 weeks", 30 to "Monthly").forEach { (days, label) ->
                            FilterChip(selected = backupState.reminderIntervalDays == days, onClick = { onBackupReminderChanged(true, days) }, label = { Text(label) })
                        }
                    }
                    Text("Reminders are only sent when your journal has changed since the last backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onExportJournal,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export journal") }

                Spacer(modifier = Modifier.height(8.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Android-managed recovery", style = MaterialTheme.typography.titleSmall)
                if (Build.VERSION.SDK_INT >= 28) {
                    Text(if (androidBackupPolicy.cloudJournalIncluded) "Cloud backup: journal allowed" else "Cloud backup: journal excluded", style = MaterialTheme.typography.bodyMedium)
                    Text(if (androidBackupPolicy.deviceTransferJournalIncluded) "Device transfer: journal allowed" else "Device transfer: journal excluded", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val included = androidBackupPolicy.cloudJournalIncluded && androidBackupPolicy.deviceTransferJournalIncluded
                    Text(if (included) "Android backup & transfer: journal allowed" else "Android backup & transfer: journal excluded", style = MaterialTheme.typography.bodyMedium)
                }
                Text("These are Aside's inclusion rules, not confirmation that Android has created a current backup. Ordinary app settings may still be backed up or transferred.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenPrivacy) { Text("Review Android backup privacy") }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onRestoreBackup,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restore backup") }
                Text(
                    text = "Aside only reads the backup you select. Where that file is stored is controlled by you and, if applicable, your storage provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showDeleteAllConfirmation = true },
                    enabled = entryCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete all journal data") }
            }
        }
                }
                SettingsSection.GENERAL -> {
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
                    Switch(
                        checked = use24Hour,
                        onCheckedChange = {
                            use24Hour = it
                            persistCurrentSettings()
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Version", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = appVersionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
                }
                SettingsSection.FEEDBACK -> {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Send beta feedback", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Aside will include: app version and build type, Android version/API level, device manufacturer/model, and whether notifications are permitted.")
                Spacer(Modifier.height(10.dp))
                Text("Aside will not automatically include journal entries, notes, mood ratings or history, hashtags, backups, exported reports, or private logs.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBetaFeedback, modifier = Modifier.fillMaxWidth()) { Text("Send feedback") }
                Text("You can review and edit the message before sending it through another app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
                }
                SettingsSection.TESTING -> {
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
                Text(
                    text = "Test timing",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = testDelaySeconds == 0L,
                        onClick = { testDelaySeconds = 0L },
                        label = { Text("Now") }
                    )
                    FilterChip(
                        selected = testDelaySeconds == 10L,
                        onClick = { testDelaySeconds = 10L },
                        label = { Text("~10 seconds") }
                    )
                }
                if (testDelaySeconds > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gives you time to lock the phone before the test arrives. Android may delay background work slightly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onSendTestNotification(testDelaySeconds) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send test check-in")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSendTestWeeklyReport(testDelaySeconds) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send test weekly report")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSendTestMonthlyReport(testDelaySeconds) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send test monthly report")
                }
            }
        }
                }
                null -> Unit
            }
        }
    }

}

private enum class SettingsSection(val title: String) {
    CHECK_INS("Check-ins"),
    REPORTS("Reports"),
    DATA("Data & backup"),
    GENERAL("General"),
    FEEDBACK("Beta feedback"),
    TESTING("Testing")
}

@Composable
private fun SettingsMenuCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun checkInTimeoutLabel(minutes: Int): String = when (minutes) {
    0 -> "Never"
    15 -> "15 minutes"
    30 -> "30 minutes"
    60 -> "1 hour"
    120 -> "2 hours"
    240 -> "4 hours"
    else -> if (minutes < 60) "$minutes minutes" else "${minutes / 60} hours"
}

private fun dayName(dayValue: Int): String = when (DayOfWeek.of(dayValue.coerceIn(1, 7))) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
}

private fun weeklyReportScheduleSummary(
    day: Int,
    useBedtimeOffset: Boolean,
    reportHour: Int,
    reportMinute: Int,
    bedtimeHour: Int,
    bedtimeMinute: Int,
    wakeHour: Int,
    wakeMinute: Int,
    use24Hour: Boolean
): String {
    if (!useBedtimeOffset) {
        return "${dayName(day)} · ${formatTime(reportHour, reportMinute, use24Hour)}"
    }

    val bedtime = LocalTime.of(bedtimeHour, bedtimeMinute)
    val wake = LocalTime.of(wakeHour, wakeMinute)
    val delivery = bedtime.minusHours(2)

    // If bedtime belongs to the following calendar day, the delivery may as well. Make that
    // explicit so “Sunday · 12:00 AM” isn't mistaken for the start of Sunday.
    val bedtimeOffsetDays = if (!bedtime.isAfter(wake)) 1 else 0
    val bedtimeAbsoluteMinutes = bedtimeOffsetDays * 24 * 60 + bedtime.hour * 60 + bedtime.minute
    val deliveryOffsetDays = ((bedtimeAbsoluteMinutes - 120).coerceAtLeast(0)) / (24 * 60)
    val suffix = if (deliveryOffsetDays > 0) " next day" else ""

    return "${dayName(day)} · 2 hours before bedtime (${formatTime(delivery.hour, delivery.minute, use24Hour)}$suffix)"
}

private fun monthlyReportScheduleSummary(
    enabled: Boolean,
    weeklyEnabled: Boolean,
    replaceWeekly: Boolean,
    useBedtimeOffset: Boolean,
    reportHour: Int,
    reportMinute: Int,
    bedtimeHour: Int,
    bedtimeMinute: Int,
    wakeHour: Int,
    wakeMinute: Int,
    use24Hour: Boolean
): String {
    if (!enabled) return "Off"
    if (weeklyEnabled && replaceWeekly) return "Replaces the last weekly report of each month"

    if (!useBedtimeOffset) {
        return "Last day of month · ${formatTime(reportHour, reportMinute, use24Hour)}"
    }
    val bedtime = LocalTime.of(bedtimeHour, bedtimeMinute)
    val wake = LocalTime.of(wakeHour, wakeMinute)
    val delivery = bedtime.minusHours(2)
    val bedtimeOffsetDays = if (!bedtime.isAfter(wake)) 1 else 0
    val bedtimeAbsoluteMinutes = bedtimeOffsetDays * 24 * 60 + bedtime.hour * 60 + bedtime.minute
    val deliveryOffsetDays = ((bedtimeAbsoluteMinutes - 120).coerceAtLeast(0)) / (24 * 60)
    val suffix = if (deliveryOffsetDays > 0) " next day" else ""
    return "Last day of month · 2 hours before bedtime (${formatTime(delivery.hour, delivery.minute, use24Hour)}$suffix)"
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
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour
    )

    var showDial by remember { mutableStateOf(true) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showDial) "Select Time" else "Enter Time",
                        style = MaterialTheme.typography.labelMedium
                    )
                    IconButton(onClick = { showDial = !showDial }) {
                        Icon(
                            imageVector = if (showDial) Icons.Default.Keyboard else Icons.Default.AccessTime,
                            contentDescription = "Toggle Input Mode"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))

                if (showDial) {
                    TimePicker(state = state)
                } else {
                    TimeInput(state = state)
                }

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
                            onConfirm(state.hour, state.minute)
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
