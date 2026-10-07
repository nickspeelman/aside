package com.nickspeelman.localjournal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nickspeelman.localjournal.privacy.PrivacyPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val PROMPTS_PER_DAY = intPreferencesKey("prompts_per_day")
        val SLEEP_START_HOUR = intPreferencesKey("sleep_start_hour")
        val SLEEP_START_MINUTE = intPreferencesKey("sleep_start_minute")
        val SLEEP_END_HOUR = intPreferencesKey("sleep_end_hour")
        val SLEEP_END_MINUTE = intPreferencesKey("sleep_end_minute")
        val IS_PAUSED = booleanPreferencesKey("is_paused")
        val CHECKIN_NOTIFICATION_TIMEOUT_MINUTES = intPreferencesKey("checkin_notification_timeout_minutes")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val WEEKLY_REPORT_ENABLED = booleanPreferencesKey("weekly_report_enabled")
        val WEEKLY_REPORT_DAY = intPreferencesKey("weekly_report_day")
        val WEEKLY_REPORT_HOUR = intPreferencesKey("weekly_report_hour")
        val WEEKLY_REPORT_MINUTE = intPreferencesKey("weekly_report_minute")
        val WEEKLY_REPORT_USE_BEDTIME_OFFSET = booleanPreferencesKey("weekly_report_use_bedtime_offset")
        val MONTHLY_REPORT_ENABLED = booleanPreferencesKey("monthly_report_enabled")
        val MONTHLY_REPORT_REPLACE_WEEKLY = booleanPreferencesKey("monthly_report_replace_weekly")
        val MONTHLY_REPORT_HOUR = intPreferencesKey("monthly_report_hour")
        val MONTHLY_REPORT_MINUTE = intPreferencesKey("monthly_report_minute")
        val MONTHLY_REPORT_USE_BEDTIME_OFFSET = booleanPreferencesKey("monthly_report_use_bedtime_offset")
        val BACKUP_REMINDER_ENABLED = booleanPreferencesKey("backup_reminder_enabled")
        val BACKUP_REMINDER_DAYS = intPreferencesKey("backup_reminder_days")

        private val PRIVACY_INITIALIZED = booleanPreferencesKey("privacy_initialized_v2")
        private val REQUIRE_JOURNAL_UNLOCK = booleanPreferencesKey("privacy_require_journal_unlock")
        private val RELOCK_POLICY = stringPreferencesKey("privacy_relock_policy")
        private val HIDE_JOURNAL_IN_RECENTS = booleanPreferencesKey("privacy_hide_journal_in_recents")
        private val PREVENT_SCREEN_CAPTURE = booleanPreferencesKey("privacy_prevent_screen_capture")
        private val CHECKIN_LOCK_SCREEN_PRIVACY = stringPreferencesKey("privacy_checkin_lock_screen")
        private val REPORT_LOCK_SCREEN_PRIVACY = stringPreferencesKey("privacy_report_lock_screen")
        private val SHOW_CHECKINS_CONNECTED = booleanPreferencesKey("privacy_show_checkins_connected")
        private val SHOW_REPORTS_CONNECTED = booleanPreferencesKey("privacy_show_reports_connected")

        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete_v2")
        private val PRIVACY_INTRO_PENDING = booleanPreferencesKey("privacy_intro_pending_v2")
        private val ALPHA4_INITIALIZED = booleanPreferencesKey("alpha4_initialized")
        private val ALPHA4_BACKUP_INTRO_PENDING = booleanPreferencesKey("alpha4_backup_intro_pending")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences -> readUserSettings(preferences) }

    /**
     * Before Alpha 2 has initialized the new fields, fall back to Alpha 1's exact notification and
     * window behavior. This matters if an already-scheduled alarm fires after an app update but
     * before the user has opened Alpha 2.
     */
    val privacySettingsFlow: Flow<PrivacySettings> = context.dataStore.data.map { preferences ->
        if (preferences[PRIVACY_INITIALIZED] == true) {
            readPrivacySettings(preferences)
        } else {
            PrivacyPresets.legacyAlpha1
        }
    }

    val onboardingCompleteFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETE] ?: false
    }

    val privacyIntroPendingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PRIVACY_INTRO_PENDING] ?: false
    }

    val alpha4BackupIntroPendingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ALPHA4_BACKUP_INTRO_PENDING] ?: false
    }

    suspend fun initializeAlpha4(isFreshInstall: Boolean): Boolean {
        val snapshot = context.dataStore.data.first()
        val alreadyAlpha4 = snapshot[ALPHA4_INITIALIZED] == true
        if (!alreadyAlpha4) {
            context.dataStore.edit { preferences ->
                preferences[ALPHA4_INITIALIZED] = true
                if (!isFreshInstall) preferences[ALPHA4_BACKUP_INTRO_PENDING] = true
            }
        }
        return alreadyAlpha4
    }

    suspend fun dismissAlpha4BackupIntroduction() {
        context.dataStore.edit { it[ALPHA4_BACKUP_INTRO_PENDING] = false }
    }

    suspend fun updateSettings(settings: UserSettings) {
        context.dataStore.edit { preferences -> writeUserSettings(preferences, settings) }
    }

    suspend fun updatePrivacySettings(settings: PrivacySettings) {
        context.dataStore.edit { preferences ->
            writePrivacySettings(preferences, settings)
            preferences[PRIVACY_INITIALIZED] = true
        }
    }

    /**
     * Initializes only Alpha 2 metadata. Existing UserSettings are never rewritten here.
     * A genuine new install starts with Balanced as its draft/default; an upgrade starts with the
     * exact Alpha 1 privacy behavior and skips onboarding.
     */
    suspend fun initializeAlpha2(isFreshInstall: Boolean) {
        val snapshot = context.dataStore.data.first()
        val needsPrivacyInitialization = snapshot[PRIVACY_INITIALIZED] != true
        val lacksOnboardingMarker = snapshot[ONBOARDING_COMPLETE] == null

        if (!needsPrivacyInitialization && !lacksOnboardingMarker) return

        context.dataStore.edit { preferences ->
            if (preferences[PRIVACY_INITIALIZED] != true) {
                val initialPrivacy = if (isFreshInstall) {
                    PrivacyPresets.balanced
                } else {
                    PrivacyPresets.legacyAlpha1
                }
                writePrivacySettings(preferences, initialPrivacy)
                preferences[PRIVACY_INITIALIZED] = true
                if (!isFreshInstall) {
                    preferences[PRIVACY_INTRO_PENDING] = true
                }
            }

            if (preferences[ONBOARDING_COMPLETE] == null) {
                preferences[ONBOARDING_COMPLETE] = !isFreshInstall
            }
        }
    }

    /** Commit all onboarding choices together so scheduling can happen exactly once afterward. */
    suspend fun completeOnboarding(settings: UserSettings, privacy: PrivacySettings) {
        context.dataStore.edit { preferences ->
            writeUserSettings(preferences, settings)
            writePrivacySettings(preferences, privacy)
            preferences[PRIVACY_INITIALIZED] = true
            preferences[PRIVACY_INTRO_PENDING] = false
            preferences[ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun dismissPrivacyIntroduction() {
        context.dataStore.edit { preferences -> preferences[PRIVACY_INTRO_PENDING] = false }
    }

    private fun readUserSettings(preferences: Preferences): UserSettings = UserSettings(
        promptsPerDay = preferences[PROMPTS_PER_DAY] ?: 3,
        sleepStartHour = preferences[SLEEP_START_HOUR] ?: 22,
        sleepStartMinute = preferences[SLEEP_START_MINUTE] ?: 0,
        sleepEndHour = preferences[SLEEP_END_HOUR] ?: 8,
        sleepEndMinute = preferences[SLEEP_END_MINUTE] ?: 0,
        isPaused = preferences[IS_PAUSED] ?: false,
        checkInNotificationTimeoutMinutes = preferences[CHECKIN_NOTIFICATION_TIMEOUT_MINUTES] ?: 0,
        use24Hour = preferences[USE_24_HOUR] ?: false,
        weeklyReportEnabled = preferences[WEEKLY_REPORT_ENABLED] ?: true,
        weeklyReportDay = preferences[WEEKLY_REPORT_DAY] ?: 7,
        weeklyReportHour = preferences[WEEKLY_REPORT_HOUR] ?: 20,
        weeklyReportMinute = preferences[WEEKLY_REPORT_MINUTE] ?: 0,
        weeklyReportUseBedtimeOffset = preferences[WEEKLY_REPORT_USE_BEDTIME_OFFSET] ?: true,
        monthlyReportEnabled = preferences[MONTHLY_REPORT_ENABLED] ?: true,
        monthlyReportReplaceWeekly = preferences[MONTHLY_REPORT_REPLACE_WEEKLY] ?: true,
        monthlyReportHour = preferences[MONTHLY_REPORT_HOUR] ?: 20,
        monthlyReportMinute = preferences[MONTHLY_REPORT_MINUTE] ?: 0,
        monthlyReportUseBedtimeOffset = preferences[MONTHLY_REPORT_USE_BEDTIME_OFFSET] ?: true,
        backupReminderEnabled = preferences[BACKUP_REMINDER_ENABLED] ?: false,
        backupReminderDays = preferences[BACKUP_REMINDER_DAYS] ?: 30
    )

    private fun readPrivacySettings(preferences: Preferences): PrivacySettings {
        val defaults = PrivacyPresets.balanced
        return PrivacySettings(
            requireJournalUnlock = preferences[REQUIRE_JOURNAL_UNLOCK] ?: defaults.requireJournalUnlock,
            relockPolicy = preferences[RELOCK_POLICY].toEnumOrDefault(defaults.relockPolicy),
            hideJournalInRecents = preferences[HIDE_JOURNAL_IN_RECENTS] ?: defaults.hideJournalInRecents,
            preventScreenCapture = preferences[PREVENT_SCREEN_CAPTURE] ?: defaults.preventScreenCapture,
            checkInLockScreenPrivacy = preferences[CHECKIN_LOCK_SCREEN_PRIVACY]
                .toEnumOrDefault(defaults.checkInLockScreenPrivacy),
            reportLockScreenPrivacy = preferences[REPORT_LOCK_SCREEN_PRIVACY]
                .toEnumOrDefault(defaults.reportLockScreenPrivacy),
            showCheckInsOnConnectedDevices = preferences[SHOW_CHECKINS_CONNECTED]
                ?: defaults.showCheckInsOnConnectedDevices,
            showReportsOnConnectedDevices = preferences[SHOW_REPORTS_CONNECTED]
                ?: defaults.showReportsOnConnectedDevices
        )
    }

    private fun writeUserSettings(preferences: androidx.datastore.preferences.core.MutablePreferences, settings: UserSettings) {
        preferences[PROMPTS_PER_DAY] = settings.promptsPerDay
        preferences[SLEEP_START_HOUR] = settings.sleepStartHour
        preferences[SLEEP_START_MINUTE] = settings.sleepStartMinute
        preferences[SLEEP_END_HOUR] = settings.sleepEndHour
        preferences[SLEEP_END_MINUTE] = settings.sleepEndMinute
        preferences[IS_PAUSED] = settings.isPaused
        preferences[CHECKIN_NOTIFICATION_TIMEOUT_MINUTES] = settings.checkInNotificationTimeoutMinutes
        preferences[USE_24_HOUR] = settings.use24Hour
        preferences[WEEKLY_REPORT_ENABLED] = settings.weeklyReportEnabled
        preferences[WEEKLY_REPORT_DAY] = settings.weeklyReportDay
        preferences[WEEKLY_REPORT_HOUR] = settings.weeklyReportHour
        preferences[WEEKLY_REPORT_MINUTE] = settings.weeklyReportMinute
        preferences[WEEKLY_REPORT_USE_BEDTIME_OFFSET] = settings.weeklyReportUseBedtimeOffset
        preferences[MONTHLY_REPORT_ENABLED] = settings.monthlyReportEnabled
        preferences[MONTHLY_REPORT_REPLACE_WEEKLY] = settings.monthlyReportReplaceWeekly
        preferences[MONTHLY_REPORT_HOUR] = settings.monthlyReportHour
        preferences[MONTHLY_REPORT_MINUTE] = settings.monthlyReportMinute
        preferences[MONTHLY_REPORT_USE_BEDTIME_OFFSET] = settings.monthlyReportUseBedtimeOffset
        preferences[BACKUP_REMINDER_ENABLED] = settings.backupReminderEnabled
        preferences[BACKUP_REMINDER_DAYS] = settings.backupReminderDays.coerceIn(1, 365)
    }

    private fun writePrivacySettings(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        settings: PrivacySettings
    ) {
        preferences[REQUIRE_JOURNAL_UNLOCK] = settings.requireJournalUnlock
        preferences[RELOCK_POLICY] = settings.relockPolicy.name
        preferences[HIDE_JOURNAL_IN_RECENTS] = settings.hideJournalInRecents
        preferences[PREVENT_SCREEN_CAPTURE] = settings.preventScreenCapture
        preferences[CHECKIN_LOCK_SCREEN_PRIVACY] = settings.checkInLockScreenPrivacy.name
        preferences[REPORT_LOCK_SCREEN_PRIVACY] = settings.reportLockScreenPrivacy.name
        preferences[SHOW_CHECKINS_CONNECTED] = settings.showCheckInsOnConnectedDevices
        preferences[SHOW_REPORTS_CONNECTED] = settings.showReportsOnConnectedDevices
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T {
    return this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
}

data class UserSettings(
    val promptsPerDay: Int = 3,
    val sleepStartHour: Int = 22,
    val sleepStartMinute: Int = 0,
    val sleepEndHour: Int = 8,
    val sleepEndMinute: Int = 0,
    val isPaused: Boolean = false,
    /** 0 keeps unanswered check-ins until the user dismisses/answers them. */
    val checkInNotificationTimeoutMinutes: Int = 0,
    val use24Hour: Boolean = false,
    val weeklyReportEnabled: Boolean = true,
    /** java.time.DayOfWeek value: Monday = 1 ... Sunday = 7. */
    val weeklyReportDay: Int = 7,
    val weeklyReportHour: Int = 20,
    val weeklyReportMinute: Int = 0,
    /** When true, the exact hour/minute fields are ignored and delivery follows bedtime - 2h. */
    val weeklyReportUseBedtimeOffset: Boolean = true,
    val monthlyReportEnabled: Boolean = true,
    /** When weekly reports are enabled, replace the final weekly report whose report day falls in each month. */
    val monthlyReportReplaceWeekly: Boolean = true,
    val monthlyReportHour: Int = 20,
    val monthlyReportMinute: Int = 0,
    /** Used for a separate monthly report (and whenever weekly reports are disabled). */
    val monthlyReportUseBedtimeOffset: Boolean = true,
    val backupReminderEnabled: Boolean = false,
    val backupReminderDays: Int = 30
)
