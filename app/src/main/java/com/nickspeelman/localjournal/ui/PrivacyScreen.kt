package com.nickspeelman.localjournal.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.data.CheckInLockScreenPrivacy
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.RelockPolicy
import com.nickspeelman.localjournal.data.ReportLockScreenPrivacy
import com.nickspeelman.localjournal.privacy.PrivacyPreset
import com.nickspeelman.localjournal.privacy.PrivacyPresets
import com.nickspeelman.localjournal.privacy.PrivacySummaryBuilder

@Composable
fun PrivacyScreen(
    settings: PrivacySettings,
    backupPolicy: AndroidBackupPolicy,
    canUseJournalUnlock: Boolean,
    onBack: () -> Unit,
    onPrivacyChanged: (PrivacySettings, AndroidBackupPolicy) -> Unit,
    onMessage: (String) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Privacy", style = MaterialTheme.typography.headlineMedium)
        }

        PrivacySummaryCard(settings = settings, backupPolicy = backupPolicy)
        PrivacyLimitsCard()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick privacy settings", style = MaterialTheme.typography.titleMedium)
            Text(
                "A preset only changes the individual settings below. You can adjust anything afterward.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrivacyPresetSelector(
                settings = settings,
                backupPolicy = backupPolicy,
                canUseJournalUnlock = canUseJournalUnlock,
                onPresetSelected = { preset ->
                    if (preset == PrivacyPreset.MAXIMUM_PRIVACY && !canUseJournalUnlock) {
                        onMessage("Set up a screen lock, fingerprint, or supported face unlock before using Maximum privacy.")
                    } else {
                        onPrivacyChanged(
                            PrivacyPresets.settingsFor(preset),
                            PrivacyPresets.backupPolicyFor(preset, Build.VERSION.SDK_INT)
                        )
                    }
                }
            )
        }

        PrivacyIndividualControls(
            settings = settings,
            canUseJournalUnlock = canUseJournalUnlock,
            onChange = { onPrivacyChanged(it, backupPolicy) },
            onMessage = onMessage
        )

        AndroidBackupControls(
            policy = backupPolicy,
            onChange = { onPrivacyChanged(settings, it) }
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PrivacySummaryCard(
    settings: PrivacySettings,
    backupPolicy: AndroidBackupPolicy = AndroidBackupPolicy.LEGACY_ALPHA3,
    heading: String = "Your privacy right now"
) {
    val summary = remember(settings, backupPolicy) {
        PrivacySummaryBuilder.build(settings, Build.VERSION.SDK_INT, backupPolicy)
    }
    var expanded by remember { mutableStateOf(false) }
    val compactBulletCount = 3 // local-first promise + two most immediately useful details
    val visibleBullets = if (expanded) summary.bullets else summary.bullets.take(compactBulletCount)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(heading, style = MaterialTheme.typography.titleMedium)
            Text(summary.title, style = MaterialTheme.typography.headlineSmall)
            summary.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(2.dp))
            visibleBullets.forEach { bullet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        bullet,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (summary.bullets.size > compactBulletCount) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (expanded) "Less" else "More")
                }
            }
        }
    }
}

@Composable
private fun PrivacyLimitsCard() {
    val limits = listOf(
        "Journal unlock protects what Aside shows inside the app; it does not separately encrypt the journal database.",
        "Manual backups, CSV exports, and shared report files are readable. Once saved or shared outside Aside, those copies are controlled by the destination you choose.",
        "Android ultimately controls system backup/transfer, lock-screen presentation, and notification bridging. Aside applies the controls Android exposes but cannot override the operating system or privileged third-party software.",
        "Maximum privacy means the strongest combination of Aside's available settings; it is not protection against a rooted or otherwise compromised device."
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Important privacy limits", style = MaterialTheme.typography.titleMedium)
            limits.forEach { limit ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        limit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyPresetSelector(
    settings: PrivacySettings,
    backupPolicy: AndroidBackupPolicy,
    canUseJournalUnlock: Boolean,
    onPresetSelected: (PrivacyPreset) -> Unit
) {
    val current = PrivacyPresets.match(settings, backupPolicy, Build.VERSION.SDK_INT)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrivacyPreset.entries.forEach { preset ->
            val disabled = preset == PrivacyPreset.MAXIMUM_PRIVACY && !canUseJournalUnlock
            OutlinedButton(
                onClick = { onPresetSelected(preset) },
                enabled = !disabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (preset) {
                            PrivacyPreset.BALANCED -> "Balanced · Recommended"
                            else -> preset.displayName
                        }
                    )
                    Text(
                        presetDescription(preset),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (current == preset) Text("Current")
            }
        }
        if (!canUseJournalUnlock) {
            Text(
                "Maximum privacy requires a device screen lock, fingerprint, or supported face unlock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (current == null) {
            Text(
                "Custom — your current settings do not exactly match a preset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AndroidBackupControls(
    policy: AndroidBackupPolicy,
    onChange: (AndroidBackupPolicy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Android backup & transfer", style = MaterialTheme.typography.titleMedium)
        Text(
            "Android can back up Aside's app data. These controls decide whether sensitive journal contents are included. Ordinary app settings may still be backed up or transferred.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (Build.VERSION.SDK_INT >= 28) {
            PrivacyControlCard {
                PrivacySwitchRow(
                    title = "Include journal in Android cloud backup",
                    checked = policy.cloudJournalIncluded,
                    onCheckedChange = { onChange(policy.copy(cloudJournalIncluded = it)) },
                    onInfo = {}
                )
                Text("If excluded, Aside still allows ordinary settings to be restored. Android controls whether and when a backup actually exists.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrivacyControlCard {
                PrivacySwitchRow(
                    title = "Include journal in device transfer",
                    checked = policy.deviceTransferJournalIncluded,
                    onCheckedChange = { onChange(policy.copy(deviceTransferJournalIncluded = it)) },
                    onInfo = {}
                )
                Text("Controls Aside's participation in Android's supported device-transfer process. Ordinary settings may still transfer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val included = policy.cloudJournalIncluded && policy.deviceTransferJournalIncluded
            PrivacyControlCard {
                PrivacySwitchRow(
                    title = "Include journal in Android backup & device transfer",
                    checked = included,
                    onCheckedChange = { onChange(AndroidBackupPolicy(it, it)) },
                    onInfo = {}
                )
                Text("Android 8.0/8.1 does not let Aside reliably distinguish cloud backup from device transfer, so journal contents are included or excluded from both together.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PrivacyIndividualControls(
    settings: PrivacySettings,
    canUseJournalUnlock: Boolean,
    onChange: (PrivacySettings) -> Unit,
    onMessage: (String) -> Unit
) {
    var explanation by remember { mutableStateOf<PrivacyExplanation?>(null) }

    explanation?.let { item ->
        AlertDialog(
            onDismissRequest = { explanation = null },
            title = { Text(item.title) },
            text = { Text(item.body) },
            confirmButton = {
                TextButton(onClick = { explanation = null }) { Text("OK") }
            }
        )
    }

    Text("Individual controls", style = MaterialTheme.typography.titleMedium)

    PrivacyControlCard {
        PrivacySwitchRow(
            title = "Require unlock to view journal",
            checked = settings.requireJournalUnlock,
            onCheckedChange = { enabled ->
                if (enabled && !canUseJournalUnlock) {
                    onMessage("Set up a screen lock, fingerprint, or supported face unlock in Android settings first.")
                } else {
                    onChange(settings.copy(requireJournalUnlock = enabled))
                }
            },
            onInfo = { explanation = explanations.getValue(PrivacyControl.UNLOCK) }
        )
        if (!canUseJournalUnlock && !settings.requireJournalUnlock) {
            Text(
                "Journal unlock protection requires a device screen lock, fingerprint, or supported face unlock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (settings.requireJournalUnlock) {
            Spacer(Modifier.height(12.dp))
            PrivacyChoiceDropdown(
                label = "Re-lock journal",
                value = settings.relockPolicy.displayName,
                values = RelockPolicy.entries.map { it.displayName },
                onSelected = { label ->
                    val policy = RelockPolicy.entries.first { it.displayName == label }
                    onChange(settings.copy(relockPolicy = policy))
                },
                onInfo = { explanation = explanations.getValue(PrivacyControl.RELOCK) }
            )
        }
    }

    PrivacyControlCard {
        PrivacySwitchRow(
            title = "Hide journal in Recent Apps",
            checked = settings.hideJournalInRecents,
            onCheckedChange = { onChange(settings.copy(hideJournalInRecents = it)) },
            onInfo = { explanation = explanations.getValue(PrivacyControl.RECENTS) }
        )
        if (Build.VERSION.SDK_INT < 33 && settings.hideJournalInRecents) {
            Text(
                "On Android 12L and earlier, Android has no reliable Recents-only protection. Aside also blocks screenshots while this is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    PrivacyControlCard {
        PrivacySwitchRow(
            title = "Prevent screenshots and screen recording",
            checked = settings.preventScreenCapture,
            onCheckedChange = { onChange(settings.copy(preventScreenCapture = it)) },
            onInfo = { explanation = explanations.getValue(PrivacyControl.CAPTURE) }
        )
    }

    PrivacyControlCard {
        Text("Lock-screen check-ins", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        PrivacyChoiceDropdown(
            label = "Check-ins",
            value = settings.checkInLockScreenPrivacy.displayName,
            values = CheckInLockScreenPrivacy.entries.map { it.displayName },
            onSelected = { label ->
                onChange(
                    settings.copy(
                        checkInLockScreenPrivacy = CheckInLockScreenPrivacy.entries
                            .first { it.displayName == label }
                    )
                )
            },
            onInfo = { explanation = explanations.getValue(PrivacyControl.CHECKINS) }
        )
    }

    PrivacyControlCard {
        Text("Lock-screen reports", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        PrivacyChoiceDropdown(
            label = "Reports",
            value = settings.reportLockScreenPrivacy.displayName,
            values = ReportLockScreenPrivacy.entries.map { it.displayName },
            onSelected = { label ->
                onChange(
                    settings.copy(
                        reportLockScreenPrivacy = ReportLockScreenPrivacy.entries
                            .first { it.displayName == label }
                    )
                )
            },
            onInfo = { explanation = explanations.getValue(PrivacyControl.REPORTS) }
        )
    }

    PrivacyControlCard {
        PrivacySwitchRow(
            title = "Show check-ins on connected devices",
            checked = settings.showCheckInsOnConnectedDevices,
            onCheckedChange = { onChange(settings.copy(showCheckInsOnConnectedDevices = it)) },
            onInfo = { explanation = explanations.getValue(PrivacyControl.CONNECTED_CHECKINS) }
        )
        Spacer(Modifier.height(12.dp))
        PrivacySwitchRow(
            title = "Show reports on connected devices",
            checked = settings.showReportsOnConnectedDevices,
            onCheckedChange = { onChange(settings.copy(showReportsOnConnectedDevices = it)) },
            onInfo = { explanation = explanations.getValue(PrivacyControl.CONNECTED_REPORTS) }
        )
    }
}

@Composable
private fun PrivacyControlCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onInfo) {
            Icon(Icons.Default.Info, contentDescription = "About $title")
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyChoiceDropdown(
    label: String,
    value: String,
    values: List<String>,
    onSelected: (String) -> Unit,
    onInfo: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                values.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
        IconButton(onClick = onInfo) {
            Icon(Icons.Default.Info, contentDescription = "About $label")
        }
    }
}

private fun presetDescription(preset: PrivacyPreset): String = when (preset) {
    PrivacyPreset.MAXIMUM_EASE -> "Least friction; journal and notifications use normal Android conveniences."
    PrivacyPreset.BALANCED -> "Easy check-ins with journal previews and report details kept more private."
    PrivacyPreset.MAXIMUM_PRIVACY -> "Requires device authentication and blocks the most casual exposure."
}

private enum class PrivacyControl {
    UNLOCK,
    RELOCK,
    RECENTS,
    CAPTURE,
    CHECKINS,
    REPORTS,
    CONNECTED_CHECKINS,
    CONNECTED_REPORTS
}

private data class PrivacyExplanation(val title: String, val body: String)

private val explanations = mapOf(
    PrivacyControl.UNLOCK to PrivacyExplanation(
        "Require unlock to view journal",
        "What this does\nRequires your fingerprint, supported face unlock, PIN, pattern, or device password before Aside shows journal history, patterns, or reports.\n\nPrivacy benefit\nSomeone using your already-unlocked phone cannot casually open your journal.\n\nTradeoff\nYou may need to authenticate when returning to your journal. Check-in capture can stay quick.\n\nWhat this doesn't do\nIt does not give the journal a separate encryption password or encrypt the database with a separate key."
    ),
    PrivacyControl.RELOCK to PrivacyExplanation(
        "Re-lock timing",
        "What this does\nControls when a successfully unlocked journal asks for authentication again.\n\nPrivacy benefit\nShorter times reduce the window in which someone else holding your unlocked phone can reopen the journal.\n\nTradeoff\nShorter times mean more authentication prompts. Aside uses app lifecycle and device-lock signals rather than unreliable background timers."
    ),
    PrivacyControl.RECENTS to PrivacyExplanation(
        "Hide journal in Recent Apps",
        "What this does\nAsks Android not to use Aside's current screen as the preview shown in the app switcher.\n\nPrivacy benefit\nJournal information is less likely to be visible while someone flips through Recent Apps.\n\nTradeoff\nOn Android 12L and earlier, Android does not provide a reliable Recents-only control, so Aside must also block screenshots while this is enabled.\n\nWhat this doesn't do\nIt does not lock the journal itself."
    ),
    PrivacyControl.CAPTURE to PrivacyExplanation(
        "Prevent screenshots and screen recording",
        "What this does\nUses Android's secure-window protection while Aside is visible.\n\nPrivacy benefit\nNormal Android screenshot and screen-recording paths cannot capture Aside.\n\nTradeoff\nYou cannot take your own screenshots of entries or reports while it is enabled.\n\nWhat this doesn't do\nIt cannot stop someone from photographing the screen with another camera."
    ),
    PrivacyControl.CHECKINS to PrivacyExplanation(
        "Lock-screen check-ins",
        "Full keeps the 1–5 rating controls available on the lock screen. Android may still require you to unlock before entering or submitting a free-form note. Private posts only a generic check-in notice when the phone is already locked, and Aside refuses rating/note actions while locked even if Android still renders an older action. Hidden asks Android not to reveal the notification on a secure lock screen.\n\nAndroid and the user's system notification settings remain the final authority over presentation, so Aside avoids placing interactive controls in a newly posted Private notification while the device is locked."
    ),
    PrivacyControl.REPORTS to PrivacyExplanation(
        "Lock-screen reports",
        "Full report puts the chart and report details directly in the notification. Notification only posts a generic 'report ready' notification and never places the chart, averages, trend, or other journal-derived report details in the notification itself. Hidden uses the same generic notification but also asks Android not to show it on a secure lock screen.\n\nBecause reports summarize your journal, Balanced defaults to Notification only. Android's system notification settings can still be more restrictive than Aside's request."
    ),
    PrivacyControl.CONNECTED_CHECKINS to PrivacyExplanation(
        "Check-ins on connected devices",
        "What this does\nWhen off, Aside marks check-in notifications as local-only so Android notification bridges are asked not to forward them to watches or other connected devices.\n\nTradeoff\nYou may lose the convenience of seeing or acting on a check-in from another device.\n\nLimit\nAndroid treats local-only as a bridging recommendation. Aside cannot guarantee what every third-party notification-access app will do."
    ),
    PrivacyControl.CONNECTED_REPORTS to PrivacyExplanation(
        "Reports on connected devices",
        "What this does\nWhen off, Aside marks report notifications as local-only so Android notification bridges are asked not to forward them.\n\nPrivacy benefit\nSensitive journal summaries are less likely to appear on a watch or other connected display.\n\nLimit\nAndroid treats local-only as a bridging recommendation. Aside cannot guarantee what every third-party notification-access app will do."
    )
)
