package com.nickspeelman.localjournal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.data.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MoodViewModel(
    private val repository: MoodRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val allEntries: StateFlow<List<MoodEntry>> = repository.allEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<UserSettings> = settingsManager.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserSettings(3, 22, 0, 8, 0, false, true)
        )

    /**
     * Suspends until DataStore has committed the settings. Callers can safely reschedule work
     * immediately after this returns without racing the write.
     */
    suspend fun saveSettings(newSettings: UserSettings) {
        settingsManager.updateSettings(newSettings)
    }

    val summaryData: StateFlow<MoodSummary> = repository.allEntries.map { entries ->
        if (entries.isEmpty()) MoodSummary()
        else {
            val avgRating = entries.map { it.rating }.average()
            val hashtags = entries
                .flatMap { it.hashtags.split(",").filter { tag -> tag.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
                .take(5)

            MoodSummary(avgRating.toFloat(), hashtags)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoodSummary())
}

data class MoodSummary(
    val averageRating: Float = 0f,
    val topHashtags: List<Pair<String, Int>> = emptyList()
)

class MoodViewModelFactory(
    private val repository: MoodRepository,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoodViewModel(repository, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
