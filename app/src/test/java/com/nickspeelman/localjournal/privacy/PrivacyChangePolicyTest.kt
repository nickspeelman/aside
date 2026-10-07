package com.nickspeelman.localjournal.privacy

import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import com.nickspeelman.localjournal.data.CheckInLockScreenPrivacy
import com.nickspeelman.localjournal.data.RelockPolicy
import com.nickspeelman.localjournal.data.ReportLockScreenPrivacy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyChangePolicyTest {
    @Test
    fun increasingPrivacyNeverRequiresFreshAuth() {
        assertFalse(
            PrivacyChangePolicy.requiresFreshAuthentication(
                PrivacyPresets.balanced,
                PrivacyPresets.maximumPrivacy
            )
        )
    }

    @Test
    fun disablingProtectionRequiresFreshAuth() {
        assertTrue(
            PrivacyChangePolicy.requiresFreshAuthentication(
                PrivacyPresets.maximumPrivacy,
                PrivacyPresets.balanced
            )
        )
    }

    @Test
    fun revealingReportsRequiresFreshAuthWhileProtected() {
        val old = PrivacyPresets.maximumPrivacy
        val new = old.copy(reportLockScreenPrivacy = ReportLockScreenPrivacy.FULL_REPORT)
        assertTrue(PrivacyChangePolicy.requiresFreshAuthentication(old, new))
    }

    @Test
    fun revealingCheckInsRequiresFreshAuthWhileProtected() {
        val old = PrivacyPresets.maximumPrivacy
        val new = old.copy(checkInLockScreenPrivacy = CheckInLockScreenPrivacy.FULL)
        assertTrue(PrivacyChangePolicy.requiresFreshAuthentication(old, new))
    }

    @Test
    fun enablingConnectedCheckInsRequiresFreshAuthWhileProtected() {
        val old = PrivacyPresets.maximumPrivacy
        val new = old.copy(showCheckInsOnConnectedDevices = true)
        assertTrue(PrivacyChangePolicy.requiresFreshAuthentication(old, new))
    }

    @Test
    fun includingJournalInAndroidBackupRequiresFreshAuthWhileProtected() {
        assertTrue(
            PrivacyChangePolicy.requiresFreshAuthentication(
                oldSettings = PrivacyPresets.maximumPrivacy,
                newSettings = PrivacyPresets.maximumPrivacy,
                oldBackupPolicy = AndroidBackupPolicy.MAXIMUM_PRIVACY,
                newBackupPolicy = AndroidBackupPolicy(
                    cloudJournalIncluded = true,
                    deviceTransferJournalIncluded = false
                )
            )
        )
    }

    @Test
    fun shorteningRelockDoesNotRequireFreshAuth() {
        val old = PrivacyPresets.maximumPrivacy.copy(relockPolicy = RelockPolicy.AFTER_5_MINUTES)
        val new = old.copy(relockPolicy = RelockPolicy.AFTER_1_MINUTE)
        assertFalse(PrivacyChangePolicy.requiresFreshAuthentication(old, new))
    }
}
