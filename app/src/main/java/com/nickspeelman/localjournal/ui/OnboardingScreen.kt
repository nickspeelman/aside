package com.nickspeelman.localjournal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.privacy.PrivacyPreset
import com.nickspeelman.localjournal.privacy.PrivacyPresets
import java.time.DayOfWeek

private enum class OnboardingPath { RECOMMENDED, CUSTOM }
private enum class OnboardingStep { WELCOME, CHECKINS, QUIET_HOURS, REPORTS, PRIVACY, NOTIFICATIONS, READY }
private enum class ReportChoice { WEEKLY, MONTHLY, BOTH, NEITHER }

@Composable
fun OnboardingScreen(
    canUseJournalUnlock: Boolean,
    onComplete: (UserSettings, PrivacySettings, AndroidBackupPolicy) -> Unit
) {
    val context = LocalContext.current
    var path by remember { mutableStateOf<OnboardingPath?>(null) }
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var settings by remember { mutableStateOf(UserSettings()) }
    var privacy by remember { mutableStateOf(PrivacyPresets.balanced) }
    var backupPolicy by remember { mutableStateOf(PrivacyPresets.backupPolicyFor(PrivacyPreset.BALANCED, Build.VERSION.SDK_INT)) }
    var privacyCustomExpanded by remember { mutableStateOf(false) }
    var permissionResultKnown by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var finishing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionResultKnown = true
        step = OnboardingStep.READY
    }

    fun nextFromPrivacy() {
        step = OnboardingStep.NOTIFICATIONS
    }

    fun goBack() {
        step = when (step) {
            OnboardingStep.WELCOME -> OnboardingStep.WELCOME
            OnboardingStep.CHECKINS -> OnboardingStep.WELCOME
            OnboardingStep.QUIET_HOURS -> OnboardingStep.CHECKINS
            OnboardingStep.REPORTS -> OnboardingStep.QUIET_HOURS
            OnboardingStep.PRIVACY -> if (path == OnboardingPath.CUSTOM) {
                OnboardingStep.REPORTS
            } else {
                OnboardingStep.WELCOME
            }
            OnboardingStep.NOTIFICATIONS -> OnboardingStep.PRIVACY
            OnboardingStep.READY -> OnboardingStep.NOTIFICATIONS
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (step != OnboardingStep.WELCOME) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::goBack, enabled = !finishing) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    if (path == OnboardingPath.CUSTOM) "Customize Aside" else "Set up Aside",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        when (step) {
            OnboardingStep.WELCOME -> {
                Text("Welcome to Aside", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Aside checks in with you occasionally, keeps a private journal of how you're feeling, and can show you patterns over time. You can change any of these settings later."
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Use recommended settings", style = MaterialTheme.typography.titleMedium)
                        Text("Start with 3 check-ins a day, quiet hours from 10 PM to 8 AM, weekly and monthly reports, and Balanced privacy.")
                        Button(
                            onClick = {
                                path = OnboardingPath.RECOMMENDED
                                settings = UserSettings()
                                privacy = PrivacyPresets.balanced
                                privacyCustomExpanded = false
                                step = OnboardingStep.PRIVACY
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Use recommended settings") }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Customize my setup", style = MaterialTheme.typography.titleMedium)
                        Text("Choose when Aside checks in, when it leaves you alone, which reports you receive, and how private your journal should be.")
                        OutlinedButton(
                            onClick = {
                                path = OnboardingPath.CUSTOM
                                step = OnboardingStep.CHECKINS
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Customize my setup") }
                    }
                }
            }

            OnboardingStep.CHECKINS -> {
                Text("Check-ins", style = MaterialTheme.typography.headlineMedium)
                Text("How often should Aside check in? You can always pause check-ins or change this later.")
                CheckInFrequencyPicker(
                    value = settings.promptsPerDay,
                    onChange = { settings = settings.copy(promptsPerDay = it) }
                )
                Button(onClick = { step = OnboardingStep.QUIET_HOURS }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }

            OnboardingStep.QUIET_HOURS -> {
                Text("When should Aside leave you alone?", style = MaterialTheme.typography.headlineMedium)
                Text("Aside won't schedule random check-ins during these quiet hours.")
                QuietHoursEditor(
                    settings = settings,
                    onChange = { settings = it }
                )
                Button(onClick = { step = OnboardingStep.REPORTS }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }

            OnboardingStep.REPORTS -> {
                Text("Reports", style = MaterialTheme.typography.headlineMedium)
                Text("Reports are optional. They summarize patterns in your journal without sending your data anywhere.")
                ReportsOnboardingEditor(
                    settings = settings,
                    onChange = { settings = it }
                )
                Button(onClick = { step = OnboardingStep.PRIVACY }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }

            OnboardingStep.PRIVACY -> {
                Text("Privacy", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (path == OnboardingPath.RECOMMENDED) {
                        "Recommended settings use Balanced privacy. Review how it works before Aside accepts your setup."
                    } else {
                        "Choose a privacy starting point. You can change every individual setting later."
                    }
                )

                PrivacySummaryCard(privacy, backupPolicy, heading = "Your privacy setup")
                PrivacyPresetSelector(
                    settings = privacy,
                    backupPolicy = backupPolicy,
                    canUseJournalUnlock = canUseJournalUnlock,
                    onPresetSelected = { preset ->
                        privacy = PrivacyPresets.settingsFor(preset)
                        backupPolicy = PrivacyPresets.backupPolicyFor(preset, Build.VERSION.SDK_INT)
                        privacyCustomExpanded = false
                    }
                )

                OutlinedButton(
                    onClick = { privacyCustomExpanded = !privacyCustomExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (privacyCustomExpanded) "Hide individual controls" else "Customize individual settings")
                }

                if (privacyCustomExpanded) {
                    PrivacyIndividualControls(
                        settings = privacy,
                        canUseJournalUnlock = canUseJournalUnlock,
                        onChange = { privacy = it },
                        onMessage = { /* disabled controls already explain themselves in place */ }
                    )
                    // Backup inclusion is part of the privacy setup; the full controls remain available later.
                    if (Build.VERSION.SDK_INT >= 28) {
                        Text("Android journal backup", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Switch(checked = backupPolicy.cloudJournalIncluded, onCheckedChange = { backupPolicy = backupPolicy.copy(cloudJournalIncluded = it) })
                            Text(" Include journal in cloud backup")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Switch(checked = backupPolicy.deviceTransferJournalIncluded, onCheckedChange = { backupPolicy = backupPolicy.copy(deviceTransferJournalIncluded = it) })
                            Text(" Include journal in device transfer")
                        }
                    } else {
                        val included = backupPolicy.cloudJournalIncluded && backupPolicy.deviceTransferJournalIncluded
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Switch(checked = included, onCheckedChange = { backupPolicy = AndroidBackupPolicy(it, it) })
                            Text(" Include journal in Android backup & device transfer")
                        }
                        Text("Android 8.0/8.1 cannot reliably separate these choices, so Balanced excludes the journal from both.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Button(onClick = ::nextFromPrivacy, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue with these privacy settings")
                }
            }

            OnboardingStep.NOTIFICATIONS -> {
                Text("Let Aside check in", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Aside uses Android notifications for occasional check-ins and any reports you choose. Aside does not use notifications for ads, engagement prompts, or marketing."
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED && !permissionResultKnown
                ) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Allow notifications") }
                    TextButton(
                        onClick = {
                            permissionResultKnown = true
                            step = OnboardingStep.READY
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue without notifications") }
                } else {
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            "Notifications are currently off. Aside still works for manual entries, and you can enable notifications later in Android settings."
                        } else {
                            "Notifications are ready. Android's own notification settings remain in your control."
                        }
                    )
                    Button(onClick = { step = OnboardingStep.READY }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue")
                    }
                }
            }

            OnboardingStep.READY -> {
                Text("You're ready", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Aside will occasionally ask how you're feeling. Choose a rating from 1–5 and optionally add a note. As your journal grows, Aside can show you patterns and reports. Everything can be changed later in Settings."
                )
                PrivacySummaryCard(privacy, backupPolicy, heading = "Your privacy setup")
                Button(
                    onClick = {
                        if (!finishing) {
                            finishing = true
                            onComplete(settings, privacy, backupPolicy)
                        }
                    },
                    enabled = !finishing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (finishing) "Starting…" else "Start using Aside")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInFrequencyPicker(value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        TextField(
            value = if (value == 3) "$value check-ins per day · Recommended" else "$value check-ins per day",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..6).forEach { count ->
                DropdownMenuItem(
                    text = { Text(if (count == 3) "$count per day · Recommended" else "$count per day") },
                    onClick = {
                        expanded = false
                        onChange(count)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuietHoursEditor(settings: UserSettings, onChange: (UserSettings) -> Unit) {
    var showBedtimePicker by remember { mutableStateOf(false) }
    var showWakePicker by remember { mutableStateOf(false) }

    if (showBedtimePicker) {
        TimePickerDialog(
            initialHour = settings.sleepStartHour,
            initialMinute = settings.sleepStartMinute,
            is24Hour = settings.use24Hour,
            onDismiss = { showBedtimePicker = false },
            onConfirm = { hour, minute ->
                onChange(settings.copy(sleepStartHour = hour, sleepStartMinute = minute))
                showBedtimePicker = false
            }
        )
    }
    if (showWakePicker) {
        TimePickerDialog(
            initialHour = settings.sleepEndHour,
            initialMinute = settings.sleepEndMinute,
            is24Hour = settings.use24Hour,
            onDismiss = { showWakePicker = false },
            onConfirm = { hour, minute ->
                onChange(settings.copy(sleepEndHour = hour, sleepEndMinute = minute))
                showWakePicker = false
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Bedtime", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { showBedtimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(onboardingTime(settings.sleepStartHour, settings.sleepStartMinute, settings.use24Hour))
            }
            Text("Wake up", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { showWakePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(onboardingTime(settings.sleepEndHour, settings.sleepEndMinute, settings.use24Hour))
            }
            Text(
                "Recommended: 10:00 PM–8:00 AM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsOnboardingEditor(settings: UserSettings, onChange: (UserSettings) -> Unit) {
    val choice = when {
        settings.weeklyReportEnabled && settings.monthlyReportEnabled -> ReportChoice.BOTH
        settings.weeklyReportEnabled -> ReportChoice.WEEKLY
        settings.monthlyReportEnabled -> ReportChoice.MONTHLY
        else -> ReportChoice.NEITHER
    }

    var dayExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            ReportChoice.entries.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = choice == option,
                        onClick = {
                            onChange(
                                when (option) {
                                    ReportChoice.WEEKLY -> settings.copy(
                                        weeklyReportEnabled = true,
                                        monthlyReportEnabled = false
                                    )
                                    ReportChoice.MONTHLY -> settings.copy(
                                        weeklyReportEnabled = false,
                                        monthlyReportEnabled = true,
                                        monthlyReportReplaceWeekly = false
                                    )
                                    ReportChoice.BOTH -> settings.copy(
                                        weeklyReportEnabled = true,
                                        monthlyReportEnabled = true,
                                        monthlyReportReplaceWeekly = true
                                    )
                                    ReportChoice.NEITHER -> settings.copy(
                                        weeklyReportEnabled = false,
                                        monthlyReportEnabled = false
                                    )
                                }
                            )
                        }
                    )
                    Text(
                        when (option) {
                            ReportChoice.WEEKLY -> "Weekly reports"
                            ReportChoice.MONTHLY -> "Monthly reports"
                            ReportChoice.BOTH -> "Both · Recommended"
                            ReportChoice.NEITHER -> "Neither"
                        }
                    )
                }
            }

            if (settings.weeklyReportEnabled) {
                Spacer(Modifier.height(12.dp))
                Text("Weekly report day", style = MaterialTheme.typography.labelLarge)
                ExposedDropdownMenuBox(
                    expanded = dayExpanded,
                    onExpandedChange = { dayExpanded = !dayExpanded }
                ) {
                    TextField(
                        value = onboardingDayName(settings.weeklyReportDay),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dayExpanded,
                        onDismissRequest = { dayExpanded = false }
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            DropdownMenuItem(
                                text = { Text(onboardingDayName(day.value)) },
                                onClick = {
                                    dayExpanded = false
                                    onChange(settings.copy(weeklyReportDay = day.value))
                                }
                            )
                        }
                    }
                }
            }

            if (settings.weeklyReportEnabled && settings.monthlyReportEnabled) {
                Spacer(Modifier.height(12.dp))
                Text("At the end of the month", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.monthlyReportReplaceWeekly,
                        onClick = { onChange(settings.copy(monthlyReportReplaceWeekly = true)) }
                    )
                    Text("Replace that week's weekly report · Recommended")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !settings.monthlyReportReplaceWeekly,
                        onClick = { onChange(settings.copy(monthlyReportReplaceWeekly = false)) }
                    )
                    Text("Send the monthly report separately")
                }
            }

            if (settings.weeklyReportEnabled || settings.monthlyReportEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Reports are scheduled for two hours before bedtime by default. Exact report times can be changed later in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun onboardingDayName(value: Int): String =
    DayOfWeek.of(value.coerceIn(1, 7)).name.lowercase().replaceFirstChar { it.uppercase() }

private fun onboardingTime(hour: Int, minute: Int, use24Hour: Boolean): String {
    return if (use24Hour) {
        "%02d:%02d".format(hour, minute)
    } else {
        val amPm = if (hour < 12) "AM" else "PM"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        "%d:%02d %s".format(h, minute, amPm)
    }
}
