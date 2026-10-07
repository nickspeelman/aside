package com.nickspeelman.localjournal.data

enum class RelockPolicy(val displayName: String) {
    IMMEDIATELY("Immediately"),
    AFTER_1_MINUTE("After 1 minute"),
    AFTER_5_MINUTES("After 5 minutes"),
    WHEN_PHONE_LOCKS("When the phone locks")
}

enum class CheckInLockScreenPrivacy(val displayName: String) {
    FULL("Full"),
    PRIVATE("Private"),
    HIDDEN("Hidden")
}

enum class ReportLockScreenPrivacy(val displayName: String) {
    FULL_REPORT("Full report"),
    NOTIFICATION_ONLY("Notification only"),
    HIDDEN("Hidden")
}

data class PrivacySettings(
    val requireJournalUnlock: Boolean,
    val relockPolicy: RelockPolicy,
    val hideJournalInRecents: Boolean,
    val preventScreenCapture: Boolean,
    val checkInLockScreenPrivacy: CheckInLockScreenPrivacy,
    val reportLockScreenPrivacy: ReportLockScreenPrivacy,
    val showCheckInsOnConnectedDevices: Boolean,
    val showReportsOnConnectedDevices: Boolean
)
