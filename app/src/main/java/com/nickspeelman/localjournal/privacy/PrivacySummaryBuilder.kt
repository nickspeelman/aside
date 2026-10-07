package com.nickspeelman.localjournal.privacy

import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.data.CheckInLockScreenPrivacy
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.ReportLockScreenPrivacy

data class PrivacySummary(
    val title: String,
    val subtitle: String?,
    val bullets: List<String>
) {
    // Kept as a convenience for tests and any non-UI consumers that need a plain-text summary.
    val body: String
        get() = bullets.joinToString(" ")
}

object PrivacySummaryBuilder {
    private const val FOUNDATION =
        "Aside itself never collects or sends your journal data. It has no account, analytics, ads, or internet access."

    fun build(settings: PrivacySettings, sdkInt: Int, backupPolicy: AndroidBackupPolicy = AndroidBackupPolicy.LEGACY_ALPHA3): PrivacySummary {
        val exactPreset = PrivacyPresets.match(settings, backupPolicy, sdkInt)
        val closest = PrivacyPresets.closest(settings)
        val closestBackupDifference = if (backupPolicy == PrivacyPresets.backupPolicyFor(closest.preset, sdkInt)) 0 else 1
        val totalDifferences = closest.differences + closestBackupDifference
        val title = exactPreset?.displayName ?: "Custom"
        val subtitle = if (exactPreset == null) {
            val noun = if (totalDifferences == 1) "setting differs" else "settings differ"
            "Closest to ${closest.preset.displayName} · $totalDifferences $noun"
        } else {
            null
        }

        val bullets = mutableListOf(FOUNDATION)

        bullets += if (sdkInt >= 28) {
            when {
                backupPolicy.cloudJournalIncluded && backupPolicy.deviceTransferJournalIncluded ->
                    "Your journal may be included in Android cloud backups and device transfers; ordinary app settings may also be backed up."
                backupPolicy.cloudJournalIncluded ->
                    "Your journal may be included in Android cloud backups but is excluded from Android device transfer; ordinary app settings may still transfer."
                backupPolicy.deviceTransferJournalIncluded ->
                    "Your journal is excluded from Android cloud backup but may be included in device transfer; ordinary app settings may still be backed up."
                else ->
                    "Your journal is excluded from Android cloud backup and device transfer; ordinary app settings may still be backed up."
            }
        } else {
            if (backupPolicy.cloudJournalIncluded && backupPolicy.deviceTransferJournalIncluded)
                "On this Android version, your journal may be included in Android backup or device transfer; Aside cannot reliably control them separately."
            else
                "On this Android version, your journal is excluded from Android backup and device transfer; Aside cannot reliably control them separately."
        }

        bullets += if (settings.requireJournalUnlock) {
            "Aside requires your phone unlock before showing your journal in the app."
        } else {
            "Your journal can be opened whenever your phone is already unlocked."
        }

        bullets += when (settings.checkInLockScreenPrivacy) {
            CheckInLockScreenPrivacy.FULL ->
                "Rating buttons can be used from your lock screen; Android may still require unlock for free-form note entry."
            CheckInLockScreenPrivacy.PRIVATE ->
                "When a check-in arrives while locked, Aside posts only a generic notice; rating and note actions require unlock."
            CheckInLockScreenPrivacy.HIDDEN ->
                "Check-ins are hidden on a secure lock screen."
        }

        bullets += when (settings.reportLockScreenPrivacy) {
            ReportLockScreenPrivacy.FULL_REPORT ->
                "Report details may appear while your phone is locked."
            ReportLockScreenPrivacy.NOTIFICATION_ONLY ->
                "Report notifications only say that a report is ready; charts, averages, and other report details stay inside Aside."
            ReportLockScreenPrivacy.HIDDEN ->
                "Report notifications contain no report details, and Aside asks Android not to show them on a secure lock screen."
        }

        bullets += if (settings.hideJournalInRecents) {
            "Journal previews are hidden in Recent Apps."
        } else {
            "Journal contents may appear in Recent Apps previews."
        }

        val recentsForcesSecureWindow = sdkInt < 33 && settings.hideJournalInRecents
        bullets += when {
            settings.preventScreenCapture -> "Screenshots and screen recording are blocked while Aside is visible."
            recentsForcesSecureWindow ->
                "On this Android version, hiding Recent Apps previews also blocks screenshots and screen recording."
            else -> "Screenshots and screen recording are allowed."
        }

        bullets += when {
            settings.showCheckInsOnConnectedDevices && settings.showReportsOnConnectedDevices ->
                "Android may mirror both check-ins and reports to connected devices."
            settings.showCheckInsOnConnectedDevices && !settings.showReportsOnConnectedDevices ->
                "Check-ins may be mirrored to connected devices; Aside asks Android to keep reports on this phone."
            !settings.showCheckInsOnConnectedDevices && settings.showReportsOnConnectedDevices ->
                "Reports may be mirrored to connected devices; Aside asks Android to keep check-ins on this phone."
            else ->
                "Aside asks Android to keep both check-ins and reports on this phone rather than bridge them to connected devices."
        }

        return PrivacySummary(
            title = title,
            subtitle = subtitle,
            bullets = bullets
        )
    }
}
