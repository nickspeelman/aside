package com.nickspeelman.localjournal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            promptsPerDay = preferences[PROMPTS_PER_DAY] ?: 3,
            sleepStartHour = preferences[SLEEP_START_HOUR] ?: 22,
            sleepStartMinute = preferences[SLEEP_START_MINUTE] ?: 0,
            sleepEndHour = preferences[SLEEP_END_HOUR] ?: 8,
            sleepEndMinute = preferences[SLEEP_END_MINUTE] ?: 0,
            isPaused = preferences[IS_PAUSED] ?: false
        )
    }

    suspend fun updateSettings(settings: UserSettings) {
        context.dataStore.edit { preferences ->
            preferences[PROMPTS_PER_DAY] = settings.promptsPerDay
            preferences[SLEEP_START_HOUR] = settings.sleepStartHour
            preferences[SLEEP_START_MINUTE] = settings.sleepStartMinute
            preferences[SLEEP_END_HOUR] = settings.sleepEndHour
            preferences[SLEEP_END_MINUTE] = settings.sleepEndMinute
            preferences[IS_PAUSED] = settings.isPaused
        }
    }
}

data class UserSettings(
    val promptsPerDay: Int,
    val sleepStartHour: Int,
    val sleepStartMinute: Int,
    val sleepEndHour: Int,
    val sleepEndMinute: Int,
    val isPaused: Boolean = false
)
