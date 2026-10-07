package com.nickspeelman.localjournal.privacy

import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.RelockPolicy

object PrivacyChangePolicy {
    /**
     * Fresh authentication is only required for privacy-reducing changes while journal protection
     * is already active. Increasing privacy never requires authentication.
     */
    fun requiresFreshAuthentication(old: PrivacySettings, new: PrivacySettings): Boolean {
        return requiresFreshAuthentication(
            oldSettings = old,
            newSettings = new,
            oldBackupPolicy = AndroidBackupPolicy.MAXIMUM_PRIVACY,
            newBackupPolicy = AndroidBackupPolicy.MAXIMUM_PRIVACY
        )
    }

    fun requiresFreshAuthentication(
        oldSettings: PrivacySettings,
        newSettings: PrivacySettings,
        oldBackupPolicy: AndroidBackupPolicy,
        newBackupPolicy: AndroidBackupPolicy
    ): Boolean {
        val old = oldSettings
        val new = newSettings
        if (!old.requireJournalUnlock) return false
        if (!new.requireJournalUnlock) return true
        if (old.hideJournalInRecents && !new.hideJournalInRecents) return true
        if (old.preventScreenCapture && !new.preventScreenCapture) return true
        if (new.checkInLockScreenPrivacy.ordinal < old.checkInLockScreenPrivacy.ordinal) return true
        if (new.reportLockScreenPrivacy.ordinal < old.reportLockScreenPrivacy.ordinal) {
            // Enum order is FULL_REPORT, NOTIFICATION_ONLY, HIDDEN, so a lower ordinal is more revealing.
            return true
        }
        if (!old.showCheckInsOnConnectedDevices && new.showCheckInsOnConnectedDevices) return true
        if (!old.showReportsOnConnectedDevices && new.showReportsOnConnectedDevices) return true
        if (!oldBackupPolicy.cloudJournalIncluded && newBackupPolicy.cloudJournalIncluded) return true
        if (!oldBackupPolicy.deviceTransferJournalIncluded && newBackupPolicy.deviceTransferJournalIncluded) return true
        if (relockBecomesLessRestrictive(old.relockPolicy, new.relockPolicy)) return true
        return false
    }

    private fun relockBecomesLessRestrictive(old: RelockPolicy, new: RelockPolicy): Boolean {
        if (old == new) return false
        if (new == RelockPolicy.IMMEDIATELY) return false
        if (old == RelockPolicy.IMMEDIATELY) return true
        if (old == RelockPolicy.AFTER_1_MINUTE && new == RelockPolicy.AFTER_5_MINUTES) return true
        if (new == RelockPolicy.WHEN_PHONE_LOCKS) return true
        if (old == RelockPolicy.WHEN_PHONE_LOCKS) return new != RelockPolicy.IMMEDIATELY
        return false
    }
}
