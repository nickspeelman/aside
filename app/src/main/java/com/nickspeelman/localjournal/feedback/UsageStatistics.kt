package com.nickspeelman.localjournal.feedback

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.data.ManualBackupState
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.UserSettings
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class UsageStatisticsSnapshot(
    val totalEntries: Int,
    val ratedEntries: Int,
    val noteOnlyEntries: Int,
    val entriesWithNotes: Int,
    val entriesWithHashtags: Int,
    val activeDays: Int,
    val entriesLast7Days: Int,
    val entriesLast30Days: Int,
    val journalSpan: String
)

object UsageStatistics {
    const val ADDRESS = "nick@nickspeelman.com"

    fun snapshot(
        entries: List<MoodEntry>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): UsageStatisticsSnapshot {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val entryDates = entries.map {
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
        }
        val spanDays = if (entryDates.isEmpty()) 0L else {
            ChronoUnit.DAYS.between(entryDates.minOrNull()!!, entryDates.maxOrNull()!!) + 1
        }
        return UsageStatisticsSnapshot(
            totalEntries = entries.size,
            ratedEntries = entries.count { it.rating != null },
            noteOnlyEntries = entries.count { it.rating == null },
            entriesWithNotes = entries.count { it.note.isNotBlank() },
            entriesWithHashtags = entries.count { it.hashtags.isNotBlank() },
            activeDays = entryDates.distinct().size,
            entriesLast7Days = entryDates.count { !it.isBefore(today.minusDays(6)) && !it.isAfter(today) },
            entriesLast30Days = entryDates.count { !it.isBefore(today.minusDays(29)) && !it.isAfter(today) },
            journalSpan = spanBucket(spanDays)
        )
    }

    fun body(
        platform: List<Pair<String, String>>,
        snapshot: UsageStatisticsSnapshot,
        settings: UserSettings,
        privacy: PrivacySettings,
        backupPolicy: AndroidBackupPolicy,
        backupState: ManualBackupState
    ): String = buildString {
        appendLine("Optional Aside usage statistics")
        appendLine("You can review or remove any line before sending.")
        appendLine()
        appendLine("App and device")
        platform.forEach { (label, value) -> appendLine("$label: $value") }
        appendLine()
        appendLine("Journal activity (aggregate counts only)")
        appendLine("Total entries: ${snapshot.totalEntries}")
        appendLine("Rated entries: ${snapshot.ratedEntries}")
        appendLine("Note-only entries: ${snapshot.noteOnlyEntries}")
        appendLine("Entries containing a note: ${snapshot.entriesWithNotes}")
        appendLine("Entries containing one or more hashtags: ${snapshot.entriesWithHashtags}")
        appendLine("Active journal days: ${snapshot.activeDays}")
        appendLine("Entries in the last 7 days: ${snapshot.entriesLast7Days}")
        appendLine("Entries in the last 30 days: ${snapshot.entriesLast30Days}")
        appendLine("Journal time span: ${snapshot.journalSpan}")
        appendLine()
        appendLine("Feature settings")
        appendLine("Check-in reminders per day: ${settings.promptsPerDay}")
        appendLine("Check-in reminders paused: ${yesNo(settings.isPaused)}")
        appendLine("Unanswered check-in timeout configured: ${yesNo(settings.checkInNotificationTimeoutMinutes > 0)}")
        appendLine("Weekly reports enabled: ${yesNo(settings.weeklyReportEnabled)}")
        appendLine("Monthly reports enabled: ${yesNo(settings.monthlyReportEnabled)}")
        appendLine("Backup reminders enabled: ${yesNo(settings.backupReminderEnabled)}")
        appendLine("Journal lock enabled: ${yesNo(privacy.requireJournalUnlock)}")
        appendLine("Hide journal in Recents: ${yesNo(privacy.hideJournalInRecents)}")
        appendLine("Screen capture blocked: ${yesNo(privacy.preventScreenCapture)}")
        appendLine("Cloud journal backup allowed: ${yesNo(backupPolicy.cloudJournalIncluded)}")
        appendLine("Device-transfer journal copy allowed: ${yesNo(backupPolicy.deviceTransferJournalIncluded)}")
        appendLine("A successful manual backup has been recorded: ${yesNo(backupState.hasSuccessfulBackup)}")
        appendLine("Recorded manual backup is current: ${yesNo(backupState.isCurrent)}")
        appendLine()
        append("No journal text, hashtag values, mood values, entry dates/times, stable identifiers, or private logs are included.")
    }

    fun launch(
        context: Context,
        entries: List<MoodEntry>,
        settings: UserSettings,
        privacy: PrivacySettings,
        backupPolicy: AndroidBackupPolicy,
        backupState: ManualBackupState
    ): Boolean {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
        val buildType = if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "debug"
        } else {
            "release"
        }
        val platform = listOf(
            "Aside version" to "${packageInfo.versionName ?: "unknown"} ($versionCode)",
            "Build type" to buildType,
            "Android" to "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Notifications permitted" to yesNo(NotificationManagerCompat.from(context).areNotificationsEnabled())
        )
        val subject = "Aside optional usage statistics"
        val body = body(platform, snapshot(entries), settings, privacy, backupPolicy, backupState)
        return EmailDraftLauncher.launch(context, ADDRESS, subject, body)
    }

    private fun spanBucket(days: Long): String = when {
        days <= 0 -> "No entries"
        days <= 7 -> "Up to 1 week"
        days <= 30 -> "More than 1 week, up to 1 month"
        days <= 90 -> "More than 1 month, up to 3 months"
        days <= 180 -> "More than 3 months, up to 6 months"
        days <= 365 -> "More than 6 months, up to 1 year"
        else -> "More than 1 year"
    }

    private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
}
