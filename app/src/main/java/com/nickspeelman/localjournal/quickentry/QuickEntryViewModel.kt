package com.nickspeelman.localjournal.quickentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nickspeelman.localjournal.data.JournalEntryWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickEntryViewModel(private val writer: JournalEntryWriter) : ViewModel() {
    var rating by mutableStateOf<Int?>(null)
        private set
    var note by mutableStateOf("")
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
        private set

    fun updateRating(value: Int?) { if (!saving) rating = value }
    fun updateNote(value: String) { if (!saving) note = value }

    fun save() {
        if (saving || saved || (rating == null && note.isBlank())) return
        saving = true
        error = null
        val capturedRating = rating
        val capturedNote = note
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { writer.create(capturedRating, capturedNote) }
            }
            if (result.isSuccess) {
                saved = true
                saving = false
            } else {
                saving = false
                error = "Couldn't save this check-in. Try again."
            }
        }
    }
}

class QuickEntryViewModelFactory(private val writer: JournalEntryWriter) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuickEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuickEntryViewModel(writer) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
