package com.nickspeelman.localjournal.privacy

import com.nickspeelman.localjournal.backup.AndroidBackupPolicy

import com.nickspeelman.localjournal.data.CheckInLockScreenPrivacy
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.RelockPolicy
import com.nickspeelman.localjournal.data.ReportLockScreenPrivacy

enum class PrivacyPreset(val displayName: String) {
    MAXIMUM_EASE("Maximum ease"),
    BALANCED("Balanced"),
    MAXIMUM_PRIVACY("Maximum privacy")
}

data class ClosestPrivacyPreset(
    val preset: PrivacyPreset,
    val differences: Int
)

object PrivacyPresets {
    val maximumEase = PrivacySettings(
        requireJournalUnlock = false,
        relockPolicy = RelockPolicy.IMMEDIATELY,
        hideJournalInRecents = false,
        preventScreenCapture = false,
        checkInLockScreenPrivacy = CheckInLockScreenPrivacy.FULL,
        reportLockScreenPrivacy = ReportLockScreenPrivacy.FULL_REPORT,
        showCheckInsOnConnectedDevices = true,
        showReportsOnConnectedDevices = true
    )

    val balanced = PrivacySettings(
        requireJournalUnlock = false,
        relockPolicy = RelockPolicy.IMMEDIATELY,
        hideJournalInRecents = true,
        preventScreenCapture = false,
        checkInLockScreenPrivacy = CheckInLockScreenPrivacy.FULL,
        reportLockScreenPrivacy = ReportLockScreenPrivacy.NOTIFICATION_ONLY,
        showCheckInsOnConnectedDevices = true,
        showReportsOnConnectedDevices = false
    )

    val maximumPrivacy = PrivacySettings(
        requireJournalUnlock = true,
        relockPolicy = RelockPolicy.IMMEDIATELY,
        hideJournalInRecents = true,
        preventScreenCapture = true,
        checkInLockScreenPrivacy = CheckInLockScreenPrivacy.PRIVATE,
        reportLockScreenPrivacy = ReportLockScreenPrivacy.HIDDEN,
        showCheckInsOnConnectedDevices = false,
        showReportsOnConnectedDevices = false
    )

    /**
     * Alpha 1 behavior. This is intentionally not one of the named Alpha 2 presets: prompts used
     * Android's default PRIVATE visibility while report notifications explicitly used PUBLIC.
     */
    val legacyAlpha1 = PrivacySettings(
        requireJournalUnlock = false,
        relockPolicy = RelockPolicy.IMMEDIATELY,
        hideJournalInRecents = false,
        preventScreenCapture = false,
        checkInLockScreenPrivacy = CheckInLockScreenPrivacy.PRIVATE,
        reportLockScreenPrivacy = ReportLockScreenPrivacy.FULL_REPORT,
        showCheckInsOnConnectedDevices = true,
        showReportsOnConnectedDevices = true
    )

    fun settingsFor(preset: PrivacyPreset): PrivacySettings = when (preset) {
        PrivacyPreset.MAXIMUM_EASE -> maximumEase
        PrivacyPreset.BALANCED -> balanced
        PrivacyPreset.MAXIMUM_PRIVACY -> maximumPrivacy
    }

    fun match(settings: PrivacySettings): PrivacyPreset? =
        PrivacyPreset.entries.firstOrNull { equivalent(settings, settingsFor(it)) }

    fun closest(settings: PrivacySettings): ClosestPrivacyPreset {
        return PrivacyPreset.entries
            .map { ClosestPrivacyPreset(it, differenceCount(settings, settingsFor(it))) }
            .minWith(compareBy<ClosestPrivacyPreset> { it.differences }.thenBy { it.preset.ordinal })
    }

    fun differenceCount(a: PrivacySettings, b: PrivacySettings): Int {
        var differences = 0
        if (a.requireJournalUnlock != b.requireJournalUnlock) differences++
        if (a.requireJournalUnlock && b.requireJournalUnlock && a.relockPolicy != b.relockPolicy) differences++
        if (a.hideJournalInRecents != b.hideJournalInRecents) differences++
        if (a.preventScreenCapture != b.preventScreenCapture) differences++
        if (a.checkInLockScreenPrivacy != b.checkInLockScreenPrivacy) differences++
        if (a.reportLockScreenPrivacy != b.reportLockScreenPrivacy) differences++
        if (a.showCheckInsOnConnectedDevices != b.showCheckInsOnConnectedDevices) differences++
        if (a.showReportsOnConnectedDevices != b.showReportsOnConnectedDevices) differences++
        return differences
    }

    fun backupPolicyFor(preset: PrivacyPreset, sdkInt: Int): AndroidBackupPolicy = when (preset) {
        PrivacyPreset.MAXIMUM_EASE -> AndroidBackupPolicy.MAXIMUM_EASE
        PrivacyPreset.BALANCED -> if (sdkInt >= 28) AndroidBackupPolicy.BALANCED else AndroidBackupPolicy.LEGACY_ANDROID_BALANCED
        PrivacyPreset.MAXIMUM_PRIVACY -> AndroidBackupPolicy.MAXIMUM_PRIVACY
    }

    fun match(settings: PrivacySettings, backupPolicy: AndroidBackupPolicy, sdkInt: Int): PrivacyPreset? =
        PrivacyPreset.entries.firstOrNull {
            equivalent(settings, settingsFor(it)) && backupPolicy == backupPolicyFor(it, sdkInt)
        }

    private fun equivalent(a: PrivacySettings, b: PrivacySettings): Boolean = differenceCount(a, b) == 0
}
