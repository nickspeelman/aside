package com.nickspeelman.localjournal.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.nickspeelman.localjournal.MainActivity
import com.nickspeelman.localjournal.R
import com.nickspeelman.localjournal.analytics.MonthlyReportAnalytics
import com.nickspeelman.localjournal.analytics.WeeklyReportAnalytics
import com.nickspeelman.localjournal.data.CheckInLockScreenPrivacy
import com.nickspeelman.localjournal.data.PrivacySettings
import com.nickspeelman.localjournal.data.ReportLockScreenPrivacy
import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.privacy.PrivacyPresets

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_PROMPT_ID = "mood_prompt_channel_v2"
        // Alpha 1 created weekly_summary_channel with PUBLIC lock-screen visibility. Android
        // persists channel behavior after creation, so Alpha 2 uses a fresh channel ID instead of
        // inheriting that legacy privacy setting on upgraded installs.
        const val CHANNEL_SUMMARY_ID = "mood_reports_channel_v3"
        const val CHANNEL_BACKUP_ID = "manual_backup_reminders_v2"
        private const val LEGACY_CHANNEL_PROMPT_ID = "mood_prompt_channel"
        private const val LEGACY_CHANNEL_SUMMARY_ID = "weekly_summary_channel"
        private const val PREVIOUS_CHANNEL_SUMMARY_ID = "mood_reports_channel_v2"
        private const val LEGACY_CHANNEL_BACKUP_ID = "manual_backup_reminders"
        const val NOTIFICATION_PROMPT_ID = 101
        const val NOTIFICATION_SUMMARY_ID = 102
        const val NOTIFICATION_MONTHLY_ID = 103
        const val NOTIFICATION_BACKUP_ID = 104
        const val EXTRA_OPEN_BACKUP = "open_manual_backup"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_OPEN_WEEKLY_REPORT = "open_weekly_report"
        const val EXTRA_OPEN_MONTHLY_REPORT = "open_monthly_report"
        const val EXTRA_ADD_NOTE_ENTRY_ID = "add_note_entry_id"
        const val EXTRA_ADD_NOTE_RATING = "add_note_rating"
        const val EXTRA_OPEN_CHECKIN_ENTRY = "open_checkin_entry"
        const val EXTRA_CHECKIN_LOCKSCREEN_PRIVACY = "checkin_lockscreen_privacy"
        const val EXTRA_SHOW_CHECKINS_CONNECTED = "show_checkins_connected"
        const val EXTRA_CHECKIN_TIMEOUT_MINUTES = "checkin_timeout_minutes"
        private const val RATING_NOTE_WINDOW_MS = 60_000L
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Aside is intentionally quiet by default. Keep prompts visually prominent while
            // suppressing sound and vibration; users can still change channel behavior in Android
            // notification settings if they actively want an audible alert.
            val promptChannel = NotificationChannel(
                CHANNEL_PROMPT_ID,
                "Mood Prompts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Random notifications to check in on your mood"
                setSound(null, null)
                enableVibration(false)
            }

            val summaryChannel = NotificationChannel(
                CHANNEL_SUMMARY_ID,
                "Mood Reports",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Your weekly and monthly mood reports"
                setSound(null, null)
                enableVibration(false)
            }

            val backupChannel = NotificationChannel(
                CHANNEL_BACKUP_ID, "Backup reminders", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Optional reminders to update your manual Aside backup"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(promptChannel)
            notificationManager.createNotificationChannel(summaryChannel)
            notificationManager.createNotificationChannel(backupChannel)

            // Notification-channel sound settings are immutable once a channel exists. These IDs
            // intentionally changed before the first public alpha so existing development installs
            // also pick up the new silent defaults instead of retaining an older audible channel.
            listOf(
                LEGACY_CHANNEL_PROMPT_ID,
                LEGACY_CHANNEL_SUMMARY_ID,
                PREVIOUS_CHANNEL_SUMMARY_ID,
                LEGACY_CHANNEL_BACKUP_ID
            ).forEach { oldChannelId ->
                if (notificationManager.getNotificationChannel(oldChannelId) != null) {
                    notificationManager.deleteNotificationChannel(oldChannelId)
                }
            }
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
    fun showMoodPrompt(
        privacy: PrivacySettings = PrivacyPresets.legacyAlpha1,
        timeoutMinutes: Int = 0
    ): Boolean {
        createNotificationChannels()
        if (!canPostNotifications(CHANNEL_PROMPT_ID)) return false

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            // Tapping a check-in should always continue the capture flow. This is especially
            // important for Private prompts posted while locked: Android unlocks/opens Aside,
            // and Aside immediately presents a fresh entry screen instead of dropping on Home.
            putExtra(EXTRA_OPEN_CHECKIN_ENTRY, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Do not rely on SystemUI to redact a fully interactive notification when Private is
        // selected. If the prompt is posted while the device is locked, hand Android a genuinely
        // generic notification containing no rating buttons or RemoteInput action at all.
        if (
            privacy.checkInLockScreenPrivacy == CheckInLockScreenPrivacy.PRIVATE &&
            isDeviceLocked()
        ) {
            return notifyPrompt(
                genericCheckInNotification(
                    text = "Unlock your phone to answer.",
                    localOnly = !privacy.showCheckInsOnConnectedDevices,
                    contentIntent = contentPendingIntent,
                    timeoutMinutes = timeoutMinutes
                )
            )
        }

        val compactView = RemoteViews(
            context.packageName,
            R.layout.notification_mood_prompt_compact
        ).also { bindRatingButtons(it, privacy, timeoutMinutes) }
        val expandedView = RemoteViews(
            context.packageName,
            R.layout.notification_mood_prompt_expanded
        ).also { bindRatingButtons(it, privacy, timeoutMinutes) }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROMPT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("How are you feeling?")
            .setContentText("1 = worst, 5 = best. Add a note if you want.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .setCustomHeadsUpContentView(compactView)
            .addAction(buildNoteAction(entryId = null, privacy = privacy, timeoutMinutes = timeoutMinutes))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)

        applyPromptTimeout(builder, timeoutMinutes)
        applyCheckInPrivacy(builder, privacy, contentPendingIntent)
        return notifyPrompt(builder.build())
    }

    /** Replaces the buttons immediately after a tap so the same prompt cannot be tapped twice. */
    fun showRatingSavingPrompt(
        rating: Int,
        privacy: PrivacySettings = PrivacyPresets.legacyAlpha1
    ): Boolean {
        createNotificationChannels()
        if (!canPostNotifications(CHANNEL_PROMPT_ID)) return false

        if (privacy.checkInLockScreenPrivacy == CheckInLockScreenPrivacy.PRIVATE && isDeviceLocked()) {
            return notifyPrompt(
                genericCheckInNotification(
                    text = "Unlock your phone to continue.",
                    localOnly = !privacy.showCheckInsOnConnectedDevices,
                    contentIntent = null
                )
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROMPT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Saving $rating / 5…")
            .setContentText("Your check-in is being saved.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        applyCheckInPrivacy(builder, privacy, contentIntent = null)
        return notifyPrompt(builder.build())
    }

    /**
     * Replaces the prompt after a rating has been saved. The journal write is already complete;
     * tapping this short-lived notification opens Aside directly to a note editor for that exact
     * entry. This avoids Android's cumbersome notification RemoteInput step after rating capture.
     */
    fun showRatingSavedPrompt(
        entryId: Int,
        rating: Int,
        privacy: PrivacySettings = PrivacyPresets.legacyAlpha1
    ): Boolean {
        createNotificationChannels()
        if (!canPostNotifications(CHANNEL_PROMPT_ID)) return false

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ADD_NOTE_ENTRY_ID, entryId)
            putExtra(EXTRA_ADD_NOTE_RATING, rating)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            entryId,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (privacy.checkInLockScreenPrivacy == CheckInLockScreenPrivacy.PRIVATE && isDeviceLocked()) {
            return notifyPrompt(
                genericCheckInNotification(
                    text = "Check-in saved. Unlock to add a note.",
                    localOnly = !privacy.showCheckInsOnConnectedDevices,
                    contentIntent = contentPendingIntent
                )
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROMPT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("$rating / 5 saved")
            .setContentText("Tap to add a note or #tags.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            // Keep the optional second step available briefly without leaving a stale prompt.
            .setTimeoutAfter(RATING_NOTE_WINDOW_MS)

        applyCheckInPrivacy(builder, privacy, contentPendingIntent)
        return notifyPrompt(builder.build())
    }

    private fun applyCheckInPrivacy(
        builder: NotificationCompat.Builder,
        privacy: PrivacySettings,
        contentIntent: PendingIntent?
    ) {
        builder.setLocalOnly(!privacy.showCheckInsOnConnectedDevices)
        when (privacy.checkInLockScreenPrivacy) {
            CheckInLockScreenPrivacy.FULL -> {
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            CheckInLockScreenPrivacy.PRIVATE -> {
                builder
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(
                        genericCheckInNotification(
                            text = "Unlock your phone to answer.",
                            localOnly = !privacy.showCheckInsOnConnectedDevices,
                            contentIntent = contentIntent
                        )
                    )
            }
            CheckInLockScreenPrivacy.HIDDEN -> {
                builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
            }
        }
    }

    private fun genericCheckInNotification(
        text: String,
        localOnly: Boolean,
        contentIntent: PendingIntent?,
        timeoutMinutes: Int = 0
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROMPT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Aside check-in")
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(localOnly)
            .setAutoCancel(contentIntent != null)
        contentIntent?.let(builder::setContentIntent)
        applyPromptTimeout(builder, timeoutMinutes)
        return builder.build()
    }

    private fun bindRatingButtons(remoteViews: RemoteViews, privacy: PrivacySettings, timeoutMinutes: Int) {
        remoteViews.setOnClickPendingIntent(R.id.rating_1, ratingPendingIntent(1, privacy, timeoutMinutes))
        remoteViews.setOnClickPendingIntent(R.id.rating_2, ratingPendingIntent(2, privacy, timeoutMinutes))
        remoteViews.setOnClickPendingIntent(R.id.rating_3, ratingPendingIntent(3, privacy, timeoutMinutes))
        remoteViews.setOnClickPendingIntent(R.id.rating_4, ratingPendingIntent(4, privacy, timeoutMinutes))
        remoteViews.setOnClickPendingIntent(R.id.rating_5, ratingPendingIntent(5, privacy, timeoutMinutes))
    }

    private fun ratingPendingIntent(rating: Int, privacy: PrivacySettings, timeoutMinutes: Int): PendingIntent {
        val intent = Intent(context, MoodReplyReceiver::class.java).apply {
            action = MoodReplyReceiver.ACTION_SET_RATING
            data = Uri.parse("aside://notification/rating/$rating")
            putExtra(MoodReplyReceiver.EXTRA_RATING, rating)
            putExtra(EXTRA_CHECKIN_LOCKSCREEN_PRIVACY, privacy.checkInLockScreenPrivacy.name)
            putExtra(EXTRA_SHOW_CHECKINS_CONNECTED, privacy.showCheckInsOnConnectedDevices)
            putExtra(EXTRA_CHECKIN_TIMEOUT_MINUTES, timeoutMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildNoteAction(entryId: Int?, privacy: PrivacySettings, timeoutMinutes: Int): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setAllowFreeFormInput(true)
            .setLabel("Optional note or #tags")
            .build()

        val replyIntent = Intent(context, MoodReplyReceiver::class.java).apply {
            action = MoodReplyReceiver.ACTION_ADD_NOTE
            data = if (entryId == null) {
                Uri.parse("aside://notification/note/new")
            } else {
                Uri.parse("aside://notification/note/$entryId")
            }
            if (entryId != null) {
                putExtra(MoodReplyReceiver.EXTRA_ENTRY_ID, entryId)
            }
            putExtra(EXTRA_CHECKIN_LOCKSCREEN_PRIVACY, privacy.checkInLockScreenPrivacy.name)
            putExtra(EXTRA_SHOW_CHECKINS_CONNECTED, privacy.showCheckInsOnConnectedDevices)
            putExtra(EXTRA_CHECKIN_TIMEOUT_MINUTES, timeoutMinutes)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "Add a note",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()
    }

    private fun applyPromptTimeout(builder: NotificationCompat.Builder, timeoutMinutes: Int) {
        if (timeoutMinutes > 0) {
            builder.setTimeoutAfter(timeoutMinutes.toLong() * 60_000L)
        }
    }

    private fun notifyPrompt(notification: Notification): Boolean {
        return try {
            notificationManager.notify(NOTIFICATION_PROMPT_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Returns true when the notification was handed to Android successfully. */
    fun showWeeklyReport(
        report: WeeklyReportAnalytics.Report,
        settings: UserSettings,
        privacy: PrivacySettings = PrivacyPresets.legacyAlpha1
    ): Boolean {
        createNotificationChannels()
        if (!canPostNotifications(CHANNEL_SUMMARY_ID)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_WEEKLY_REPORT, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            17,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val previousText = report.previousAverage?.let {
            " · last week ${String.format(java.util.Locale.getDefault(), "%.1f", it)}"
        }.orEmpty()
        val builder = when (privacy.reportLockScreenPrivacy) {
            ReportLockScreenPrivacy.FULL_REPORT -> {
                val displayMetrics = context.resources.displayMetrics
                val horizontalSafetyMarginPx = (48f * displayMetrics.density).toInt()
                val dashboardWidthPx = (displayMetrics.widthPixels - horizontalSafetyMarginPx)
                    .coerceAtLeast(320)
                    .coerceAtMost(640)
                val dashboard = WeeklyReportRenderer.render(report, settings, dashboardWidthPx)
                NotificationCompat.Builder(context, CHANNEL_SUMMARY_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("Weekly report · ${WeeklyReportRenderer.headline(report)}")
                    .setContentText("${report.checkInCount} check-ins$previousText")
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(dashboard)
                            .setSummaryText("${report.checkInCount} check-ins$previousText")
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setLocalOnly(!privacy.showReportsOnConnectedDevices)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            ReportLockScreenPrivacy.NOTIFICATION_ONLY -> {
                // The notification object itself contains no journal-derived report data. This is
                // intentionally stronger and more predictable than depending on publicVersion.
                genericReportNotificationBuilder("weekly", pendingIntent, privacy)
            }
            ReportLockScreenPrivacy.HIDDEN -> {
                hiddenReportNotificationBuilder("weekly", pendingIntent, privacy)
            }
        }

        return try {
            notificationManager.notify(NOTIFICATION_SUMMARY_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Returns true when the notification was handed to Android successfully. */
    fun showMonthlyReport(
        report: MonthlyReportAnalytics.Report,
        settings: UserSettings,
        privacy: PrivacySettings = PrivacyPresets.legacyAlpha1
    ): Boolean {
        createNotificationChannels()
        if (!canPostNotifications(CHANNEL_SUMMARY_ID)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_MONTHLY_REPORT, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            18,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val previousText = report.previousAverage?.let {
            " · prior 30 days ${String.format(java.util.Locale.getDefault(), "%.1f", it)}"
        }.orEmpty()
        val builder = when (privacy.reportLockScreenPrivacy) {
            ReportLockScreenPrivacy.FULL_REPORT -> {
                val displayMetrics = context.resources.displayMetrics
                val horizontalSafetyMarginPx = (48f * displayMetrics.density).toInt()
                val dashboardWidthPx = (displayMetrics.widthPixels - horizontalSafetyMarginPx)
                    .coerceAtLeast(320)
                    .coerceAtMost(640)
                val dashboard = MonthlyReportRenderer.render(report, settings, dashboardWidthPx)
                NotificationCompat.Builder(context, CHANNEL_SUMMARY_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("Monthly report · ${MonthlyReportRenderer.headline(report)}")
                    .setContentText("${report.checkInCount} check-ins$previousText")
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(dashboard)
                            .setSummaryText("${report.checkInCount} check-ins$previousText")
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setLocalOnly(!privacy.showReportsOnConnectedDevices)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            ReportLockScreenPrivacy.NOTIFICATION_ONLY -> {
                genericReportNotificationBuilder("monthly", pendingIntent, privacy)
            }
            ReportLockScreenPrivacy.HIDDEN -> {
                hiddenReportNotificationBuilder("monthly", pendingIntent, privacy)
            }
        }

        return try {
            notificationManager.notify(NOTIFICATION_MONTHLY_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun hiddenReportNotificationBuilder(
        reportType: String,
        contentIntent: PendingIntent,
        privacy: PrivacySettings
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_SUMMARY_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Your $reportType report is ready")
            .setContentText("Open Aside to view it.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setLocalOnly(!privacy.showReportsOnConnectedDevices)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
    }

    private fun genericReportNotificationBuilder(
        reportType: String,
        contentIntent: PendingIntent,
        privacy: PrivacySettings
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_SUMMARY_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Your $reportType report is ready")
            .setContentText("Open Aside to view it.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setLocalOnly(!privacy.showReportsOnConnectedDevices)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun isDeviceLocked(): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguard.isDeviceLocked
        } else {
            @Suppress("DEPRECATION")
            keyguard.isKeyguardLocked
        }
    }

    fun showBackupReminder(): Boolean {
        if (!canPostNotifications(CHANNEL_BACKUP_ID)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_BACKUP, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 104, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_BACKUP_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to update your backup")
            .setContentText("Your journal has changed since your last backup.")
            .setContentIntent(pending).setAutoCancel(true).setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BACKUP_ID, notification)
        return true
    }

}
