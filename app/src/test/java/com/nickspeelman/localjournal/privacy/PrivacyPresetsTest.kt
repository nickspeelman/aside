package com.nickspeelman.localjournal.privacy

import com.nickspeelman.localjournal.data.CheckInLockScreenPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPresetsTest {
    @Test
    fun balancedMatchesBalanced() {
        assertEquals(PrivacyPreset.BALANCED, PrivacyPresets.match(PrivacyPresets.balanced))
    }

    @Test
    fun irrelevantRelockDoesNotMakeUnlockedPresetCustom() {
        val changed = PrivacyPresets.balanced.copy(
            relockPolicy = com.nickspeelman.localjournal.data.RelockPolicy.AFTER_5_MINUTES
        )
        assertEquals(PrivacyPreset.BALANCED, PrivacyPresets.match(changed))
    }

    @Test
    fun individualChangeBecomesCustom() {
        val changed = PrivacyPresets.balanced.copy(
            checkInLockScreenPrivacy = CheckInLockScreenPrivacy.PRIVATE
        )
        assertNull(PrivacyPresets.match(changed))
        assertEquals(1, PrivacyPresets.closest(changed).differences)
    }

    @Test
    fun legacyAlpha1IsCustom() {
        assertNull(PrivacyPresets.match(PrivacyPresets.legacyAlpha1))
        assertEquals(PrivacyPreset.MAXIMUM_EASE, PrivacyPresets.closest(PrivacyPresets.legacyAlpha1).preset)
    }

    @Test
    fun privacySummaryAlwaysStatesLocalFoundation() {
        val summary = PrivacySummaryBuilder.build(PrivacyPresets.maximumPrivacy, sdkInt = 33)
        assertTrue(summary.body.contains("never collects or sends your journal data"))
        assertTrue(summary.body.contains("no account, analytics, ads, or internet access"))
    }

    @Test
    fun oldAndroidSummaryExplainsRecentsScreenshotFallback() {
        val summary = PrivacySummaryBuilder.build(PrivacyPresets.balanced, sdkInt = 32)
        assertTrue(summary.body.contains("also blocks screenshots and screen recording"))
    }
}
