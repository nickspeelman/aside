package com.nickspeelman.localjournal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.MoodEntry
import com.nickspeelman.localjournal.data.MoodRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoodReplyReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()

        if (!replyText.isNullOrBlank()) {
            val parsedEntry = ParserUtils.parseMoodInput(replyText)
            
            scope.launch {
                val db = MoodDatabase.getDatabase(context)
                val repository = MoodRepository(db.moodDao())
                repository.insert(parsedEntry)

                // Update notification to confirm receipt
                val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_PROMPT_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("Response Saved")
                    .setContentText("Your mood has been recorded.")
                    .build()

                try {
                    NotificationManagerCompat.from(context).notify(NotificationHelper.NOTIFICATION_PROMPT_ID, notification)
                } catch (e: SecurityException) {
                    // Handle missing notification permission if necessary
                }
            }
        }
    }
}

object ParserUtils {
    fun parseMoodInput(input: String): MoodEntry {
        val parts = input.trim().split(Regex("\\s+"))
        val rating = parts.firstOrNull()?.toIntOrNull() ?: 5 // Default to 5 if unparseable

        val noteParts = mutableListOf<String>()
        val hashtagParts = mutableListOf<String>()

        parts.drop(1).forEach { part ->
            if (part.startsWith("#")) {
                hashtagParts.add(part)
            } else {
                noteParts.add(part)
            }
        }

        return MoodEntry(
            rating = rating.coerceIn(1, 10),
            note = noteParts.joinToString(" "),
            hashtags = hashtagParts.joinToString(",")
        )
    }
}
