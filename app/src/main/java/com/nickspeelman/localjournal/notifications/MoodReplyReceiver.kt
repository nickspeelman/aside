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
import kotlinx.coroutines.launch

class MoodReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput
            ?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
            ?.toString()

        if (replyText.isNullOrBlank()) return

        // BroadcastReceiver.onReceive() is short-lived. goAsync() keeps the process alive while
        // Room performs the database write on a background dispatcher.
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parsedEntry = ParserUtils.parseMoodInput(replyText)
                val db = MoodDatabase.getDatabase(appContext)
                val repository = MoodRepository(db.moodDao())
                repository.insert(parsedEntry)

                if (NotificationHelper(appContext).canPostNotifications()) {
                    val notification = NotificationCompat.Builder(
                        appContext,
                        NotificationHelper.CHANNEL_PROMPT_ID
                    )
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("Response Saved")
                        .setContentText("Your mood has been recorded.")
                        .setAutoCancel(true)
                        .build()

                    try {
                        NotificationManagerCompat.from(appContext).notify(
                            NotificationHelper.NOTIFICATION_PROMPT_ID,
                            notification
                        )
                    } catch (_: SecurityException) {
                        // Permission may have been revoked between the check and notify().
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object ParserUtils {
    fun parseMoodInput(input: String): MoodEntry {
        val parts = input.trim().split(Regex("\\s+"))
        val rating = parts.firstOrNull()?.toIntOrNull() ?: 3 // Default to 3 if unparseable

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
            rating = rating.coerceIn(1, 5),
            note = noteParts.joinToString(" "),
            hashtags = hashtagParts.joinToString(",")
        )
    }
}
