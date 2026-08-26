package com.nickspeelman.localjournal.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.nickspeelman.localjournal.MainActivity

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_PROMPT_ID = "mood_prompt_channel"
        const val CHANNEL_SUMMARY_ID = "weekly_summary_channel"
        const val NOTIFICATION_PROMPT_ID = 101
        const val NOTIFICATION_SUMMARY_ID = 102
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val promptChannel = NotificationChannel(
                CHANNEL_PROMPT_ID,
                "Mood Prompts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Random notifications to check in on your mood"
            }

            val summaryChannel = NotificationChannel(
                CHANNEL_SUMMARY_ID,
                "Weekly Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Your weekly mood report"
            }

            notificationManager.createNotificationChannel(promptChannel)
            notificationManager.createNotificationChannel(summaryChannel)
        }
    }

    fun canPostNotifications(channelId: String? = CHANNEL_PROMPT_ID): Boolean {
        val runtimePermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (!runtimePermissionGranted ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelId != null) {
            val channel = notificationManager.getNotificationChannel(channelId)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }

        return true
    }

    /** Returns true when the notification was handed to Android successfully. */
    fun showMoodPrompt(): Boolean {
        createNotificationChannels()
        if (!canPostNotifications(CHANNEL_PROMPT_ID)) return false

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
            setLabel("1-5 Note #Tags, e.g. '4 at the beach #friends'")
            build()
        }

        val replyIntent = Intent(context, MoodReplyReceiver::class.java)
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PROMPT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("How are you feeling?")
            .setContentText("On a scale of 1-5, how do you feel right now?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            notificationManager.notify(NOTIFICATION_PROMPT_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Returns true when the notification was handed to Android successfully. */
    fun showWeeklySummary(summaryText: String): Boolean {
        createNotificationChannels()
        if (!canPostNotifications(CHANNEL_SUMMARY_ID)) return false

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Your Weekly Mood Summary")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            notificationManager.notify(NOTIFICATION_SUMMARY_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
