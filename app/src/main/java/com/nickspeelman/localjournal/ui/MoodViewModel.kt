package com.nickspeelman.localjournal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nickspeelman.localjournal.analytics.MoodAnalytics
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.JournalEntryWriter
import com.nickspeelman.localjournal.data.ManualBackupManager
import com.nickspeelman.localjournal.data.ManualBackupState
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.privacy.PrivacyPresets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MoodViewModel(
    private val repository: MoodRepository,
    private val settingsManager: SettingsManager,
    private val manualBackupManager: ManualBackupManager
) : ViewModel() {

    private val entryWriter = JournalEntryWriter(repository)

    val allEntries: StateFlow<List<MoodEntry>> = repository.allEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<UserSettings> = settingsManager.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserSettings()
        )

    val privacySettings: StateFlow<PrivacySettings> = settingsManager.privacySettingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PrivacyPresets.legacyAlpha1
        )

    val onboardingComplete: StateFlow<Boolean> = settingsManager.onboardingCompleteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val manualBackupState: StateFlow<ManualBackupState> = manualBackupManager.stateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManualBackupState())

    suspend fun currentJournalRevision(): Long = manualBackupManager.current().journalRevision
    suspend fun recordSuccessfulBackup(createdAt: Long, entryCount: Int, representedRevision: Long) = manualBackupManager.recordSuccessfulBackup(createdAt, entryCount, representedRevision)
    suspend fun recordSuccessfulRestore(createdAt: Long, entryCount: Int) = manualBackupManager.recordSuccessfulRestore(createdAt, entryCount)
    suspend fun dismissBackupStoragePrivacyNotice() = manualBackupManager.dismissStoragePrivacyNotice()
    suspend fun setBackupReminder(enabled: Boolean, intervalDays: Int) = manualBackupManager.setReminder(enabled, intervalDays)

    val privacyIntroPending: StateFlow<Boolean> = settingsManager.privacyIntroPendingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Suspends until DataStore has committed the settings. Callers can safely reschedule work
     * immediately after this returns without racing the write.
     */
    suspend fun saveSettings(newSettings: UserSettings) {
        settingsManager.updateSettings(newSettings)
    }

    suspend fun savePrivacySettings(newSettings: PrivacySettings) {
        settingsManager.updatePrivacySettings(newSettings)
    }

    suspend fun currentPrivacySettings(): PrivacySettings =
        settingsManager.privacySettingsFlow.first()

    suspend fun completeOnboarding(settings: UserSettings, privacy: PrivacySettings) {
        settingsManager.completeOnboarding(settings, privacy)
    }

    suspend fun dismissPrivacyIntroduction() {
        settingsManager.dismissPrivacyIntroduction()
    }

    /** Save an in-app check-in with rating collected separately from note/#tags text. */
    suspend fun addEntry(rating: Int?, content: String) { entryWriter.create(rating, content) }

    /** Retained for compact notification compatibility and older call sites. */
    suspend fun addEntry(input: String) { entryWriter.createCompact(input) }

    /** Attach optional note/#tags text to a rating that was already saved from a notification. */
    suspend fun addNoteToEntry(entryId: Int, content: String): Boolean =
        entryWriter.attachContent(entryId, content)

    suspend fun updateEntry(entry: MoodEntry) = repository.update(entry)

    suspend fun deleteEntry(entry: MoodEntry) = repository.delete(entry)

    /** Used by the short Undo window after a History deletion. A non-zero id is preserved. */
    suspend fun restoreDeletedEntry(entry: MoodEntry) = repository.insert(entry)

    suspend fun deleteAllJournalData() = repository.deleteAll()

    suspend fun journalSnapshot(): List<MoodEntry> = repository.snapshot()

    suspend fun replaceJournal(entries: List<MoodEntry>) = repository.replaceAll(entries)

    val summaryData: StateFlow<MoodAnalytics.HomeSummary> = allEntries
        .map { entries -> MoodAnalytics.homeSummary(entries) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            MoodAnalytics.HomeSummary()
        )
}

class MoodViewModelFactory(
    private val repository: MoodRepository,
    private val settingsManager: SettingsManager,
    private val manualBackupManager: ManualBackupManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoodViewModel(repository, settingsManager, manualBackupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
