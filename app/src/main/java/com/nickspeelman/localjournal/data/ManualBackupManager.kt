package com.nickspeelman.localjournal.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

private val Context.manualBackupDataStore by preferencesDataStore(name = "manual_backup_state")

data class ManualBackupState(
    val trackingInitialized: Boolean = false,
    val statusKnown: Boolean = true,
    val journalRevision: Long = 0,
    val lastSuccessfulBackupAt: Long? = null,
    val lastSuccessfulBackupRevision: Long? = null,
    val lastSuccessfulBackupEntryCount: Int? = null,
    val entriesAddedSinceBackup: Int = 0,
    val otherChangesSinceBackup: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderIntervalDays: Int = 30,
    val reminderAnchorAt: Long? = null,
    val lastReminderAt: Long? = null,
    val storagePrivacyNoticeDismissed: Boolean = false
) {
    val hasSuccessfulBackup get() = lastSuccessfulBackupAt != null && lastSuccessfulBackupRevision != null
    val isCurrent get() = hasSuccessfulBackup && journalRevision == lastSuccessfulBackupRevision
}

class ManualBackupManager(private val context: Context) {
    companion object {
        private val INITIALIZED = booleanPreferencesKey("initialized")
        private val STATUS_KNOWN = booleanPreferencesKey("status_known")
        private val REVISION = longPreferencesKey("journal_revision")
        private val BACKUP_AT = longPreferencesKey("last_backup_at")
        private val BACKUP_REVISION = longPreferencesKey("last_backup_revision")
        private val BACKUP_COUNT = intPreferencesKey("last_backup_entry_count")
        private val ADDED = intPreferencesKey("entries_added_since_backup")
        private val OTHER = booleanPreferencesKey("other_changes_since_backup")
        private val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_DAYS = intPreferencesKey("reminder_interval_days")
        private val REMINDER_ANCHOR = longPreferencesKey("reminder_anchor_at")
        private val LAST_REMINDER = longPreferencesKey("last_reminder_at")
        private val STORAGE_PRIVACY_NOTICE_DISMISSED = booleanPreferencesKey("storage_privacy_notice_dismissed")
    }

    val stateFlow: Flow<ManualBackupState> = combine(
        context.manualBackupDataStore.data,
        SettingsManager(context).settingsFlow
    ) { backupPrefs, settings ->
        read(backupPrefs).copy(
            reminderEnabled = settings.backupReminderEnabled,
            reminderIntervalDays = settings.backupReminderDays
        )
    }

    suspend fun initialize(isFreshInstall: Boolean) {
        val p = context.manualBackupDataStore.data.first()
        // Alpha 4 moves the user's reminder preference into portable ordinary settings while
        // leaving manual-backup history device-local. Migrate Alpha 3's values once.
        if (p[REMINDER_ENABLED] != null || p[REMINDER_DAYS] != null) {
            val manager = SettingsManager(context)
            val current = manager.settingsFlow.first()
            manager.updateSettings(current.copy(
                backupReminderEnabled = p[REMINDER_ENABLED] ?: current.backupReminderEnabled,
                backupReminderDays = p[REMINDER_DAYS] ?: current.backupReminderDays
            ))
            context.manualBackupDataStore.edit { it.remove(REMINDER_ENABLED); it.remove(REMINDER_DAYS) }
        }
        if (p[INITIALIZED] == true) return
        context.manualBackupDataStore.edit {
            it[INITIALIZED] = true
            it[STATUS_KNOWN] = isFreshInstall
            it[REVISION] = 0L
        }
    }

    suspend fun recordInsert() = mutate(added = true)
    suspend fun recordOtherMutation() = mutate(added = false)
    private suspend fun mutate(added: Boolean) {
        context.manualBackupDataStore.edit {
            it[REVISION] = (it[REVISION] ?: 0L) + 1L
            if (added) it[ADDED] = (it[ADDED] ?: 0) + 1 else it[OTHER] = true
        }
    }

    suspend fun recordSuccessfulBackup(createdAt: Long, entryCount: Int, representedRevision: Long) {
        context.manualBackupDataStore.edit {
            it[STATUS_KNOWN] = true
            it[BACKUP_AT] = createdAt
            it[BACKUP_REVISION] = representedRevision
            it[BACKUP_COUNT] = entryCount
            it[ADDED] = 0
            it[OTHER] = false
            it.remove(LAST_REMINDER)
            it[REMINDER_ANCHOR] = createdAt
        }
    }

    suspend fun recordSuccessfulRestore(backupCreatedAt: Long, entryCount: Int) {
        context.manualBackupDataStore.edit {
            val revision = (it[REVISION] ?: 0L) + 1L
            it[REVISION] = revision
            it[STATUS_KNOWN] = true
            it[BACKUP_AT] = backupCreatedAt
            it[BACKUP_REVISION] = revision
            it[BACKUP_COUNT] = entryCount
            it[ADDED] = 0
            it[OTHER] = false
            it.remove(LAST_REMINDER)
            it[REMINDER_ANCHOR] = System.currentTimeMillis()
        }
    }

    suspend fun setReminder(enabled: Boolean, intervalDays: Int) {
        val days = intervalDays.coerceIn(1, 365)
        val settingsManager = SettingsManager(context)
        val currentSettings = settingsManager.settingsFlow.first()
        settingsManager.updateSettings(currentSettings.copy(backupReminderEnabled = enabled, backupReminderDays = days))
        context.manualBackupDataStore.edit {
            // Remove legacy portable copies; only scheduling history remains device-local here.
            it.remove(REMINDER_ENABLED)
            it.remove(REMINDER_DAYS)
            if (enabled && it[REMINDER_ANCHOR] == null) it[REMINDER_ANCHOR] = System.currentTimeMillis()
        }
    }

    suspend fun dismissStoragePrivacyNotice() {
        context.manualBackupDataStore.edit { it[STORAGE_PRIVACY_NOTICE_DISMISSED] = true }
    }

    suspend fun markReminderSent(now: Long = System.currentTimeMillis()) {
        context.manualBackupDataStore.edit { it[LAST_REMINDER] = now }
    }

    suspend fun current(): ManualBackupState = stateFlow.first()

    private fun read(p: Preferences) = ManualBackupState(
        trackingInitialized = p[INITIALIZED] ?: false,
        statusKnown = p[STATUS_KNOWN] ?: true,
        journalRevision = p[REVISION] ?: 0L,
        lastSuccessfulBackupAt = p[BACKUP_AT],
        lastSuccessfulBackupRevision = p[BACKUP_REVISION],
        lastSuccessfulBackupEntryCount = p[BACKUP_COUNT],
        entriesAddedSinceBackup = p[ADDED] ?: 0,
        otherChangesSinceBackup = p[OTHER] ?: false,
        reminderEnabled = p[REMINDER_ENABLED] ?: false,
        reminderIntervalDays = p[REMINDER_DAYS] ?: 30,
        reminderAnchorAt = p[REMINDER_ANCHOR],
        lastReminderAt = p[LAST_REMINDER],
        storagePrivacyNoticeDismissed = p[STORAGE_PRIVACY_NOTICE_DISMISSED] ?: false
    )
}
