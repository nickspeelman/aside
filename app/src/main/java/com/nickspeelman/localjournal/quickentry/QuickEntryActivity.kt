package com.nickspeelman.localjournal.quickentry

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nickspeelman.localjournal.data.JournalEntryWriter
import com.nickspeelman.localjournal.data.ManualBackupManager
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.security.applyAsideWindowPrivacy
import com.nickspeelman.localjournal.ui.theme.LocalJournalTheme
import kotlinx.coroutines.flow.first

class QuickEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Protect typed note text conservatively until the user's stored privacy policy loads.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val app = applicationContext
        val writer = JournalEntryWriter(
            MoodRepository(MoodDatabase.getDatabase(app).moodDao(), ManualBackupManager(app))
        )
        val factory = QuickEntryViewModelFactory(writer)

        setContent {
            val privacy by androidx.compose.runtime.produceState<PrivacySettings?>(null) {
                value = SettingsManager(app).privacySettingsFlow.first()
            }
            LaunchedEffect(privacy) {
                privacy?.let { applyAsideWindowPrivacy(this@QuickEntryActivity, it) }
            }
            LocalJournalTheme {
                val quickEntryViewModel: QuickEntryViewModel = viewModel(factory = factory)
                QuickEntryScreen(
                    state = quickEntryViewModel,
                    onSaved = { finishAndRemoveTask() }
                )
            }
        }
    }
}

@Composable
private fun QuickEntryScreen(state: QuickEntryViewModel, onSaved: () -> Unit) {
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("How are you feeling?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { value ->
                    val modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = when (value) { 1 -> "Mood rating 1 of 5, worst"; 5 -> "Mood rating 5 of 5, best"; else -> "Mood rating $value of 5" }
                            selected = state.rating == value
                        }
                    if (state.rating == value) {
                        FilledTonalButton(
                            onClick = { state.updateRating(null) },
                            modifier = modifier,
                            contentPadding = PaddingValues(0.dp),
                            enabled = !state.saving
                        ) { Text("$value") }
                    } else {
                        OutlinedButton(
                            onClick = { state.updateRating(value) },
                            modifier = modifier,
                            contentPadding = PaddingValues(0.dp),
                            enabled = !state.saving
                        ) { Text("$value") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1 · worst", style = MaterialTheme.typography.labelSmall)
                Text("5 · best", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = state::updateNote,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Optional note or #tags") },
                minLines = 3,
                maxLines = 6,
                enabled = !state.saving
            )
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { state.save() },
                enabled = !state.saving && (state.rating != null || state.note.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        }
    }
}
