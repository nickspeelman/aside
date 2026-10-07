package com.nickspeelman.localjournal.privacy

import com.nickspeelman.localjournal.backup.AndroidBackupPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class Alpha4BackupPresetTest {
    @Test fun modernBalancedExcludesCloudButAllowsD2d() {
        assertEquals(AndroidBackupPolicy(false, true), PrivacyPresets.backupPolicyFor(PrivacyPreset.BALANCED, 28))
    }

    @Test fun androidEightBalancedUsesCombinedOff() {
        assertEquals(AndroidBackupPolicy(false, false), PrivacyPresets.backupPolicyFor(PrivacyPreset.BALANCED, 27))
    }

    @Test fun easeAndPrivacyRemainUnambiguous() {
        assertEquals(AndroidBackupPolicy(true, true), PrivacyPresets.backupPolicyFor(PrivacyPreset.MAXIMUM_EASE, 37))
        assertEquals(AndroidBackupPolicy(false, false), PrivacyPresets.backupPolicyFor(PrivacyPreset.MAXIMUM_PRIVACY, 37))
    }
}
