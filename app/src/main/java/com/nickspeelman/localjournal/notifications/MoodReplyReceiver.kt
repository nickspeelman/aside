package com.nickspeelman.localjournal.notifications

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.nickspeelman.localjournal.data.CheckInLockScreenPrivacy
import com.nickspeelman.localjournal.data.MoodDatabase
import com.nickspeelman.localjournal.data.JournalEntryWriter
import com.nickspeelman.localjournal.data.MoodRepository
import com.nickspeelman.localjournal.data.ManualBackupManager
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.privacy.PrivacyPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MoodReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SET_RATING -> handleRating(context, intent)
            ACTION_ADD_NOTE -> handleNote(context, intent)
            else -> handleLegacyReply(context, intent)
        }
    }

    private fun handleRating(context: Context, intent: Intent) {
        val rating = intent.getIntExtra(EXTRA_RATING, 0)
        if (rating !in 1..5) return

        val appContext = context.applicationContext
        val postedPrivacy = privacyEmbeddedInNotification(intent)
        val postedTimeoutMinutes = timeoutEmbeddedInNotification(intent)

        // A Private notification posted while the phone was unlocked can remain visible after the
        // phone is locked on some Android builds. Never let a stale visible action bypass Aside's
        // privacy choice even if SystemUI still renders the button.
        if (interactionBlockedWhileLocked(appContext, postedPrivacy)) {
            restoreBlockedPrompt(appContext, postedPrivacy, postedTimeoutMinutes)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            var effectivePrivacy = postedPrivacy
            var effectiveTimeoutMinutes = postedTimeoutMinutes
            try {
                val settingsManager = SettingsManager(appContext)
                val privacy = settingsManager.privacySettingsFlow.first()
                val timeoutMinutes = settingsManager.settingsFlow.first().checkInNotificationTimeoutMinutes
                effectivePrivacy = privacy
                effectiveTimeoutMinutes = timeoutMinutes
                if (interactionBlockedWhileLocked(appContext, privacy)) {
                    restoreBlockedPrompt(appContext, privacy, timeoutMinutes)
                    return@launch
                }

                // Only replace the controls with Saving after the current privacy setting has been
                // checked. This prevents a just-strengthened setting from accepting an old action.
                NotificationHelper(appContext).showRatingSavingPrompt(rating, privacy)

                val db = MoodDatabase.getDatabase(appContext)
                val repository = MoodRepository(db.moodDao(), ManualBackupManager(appContext))
                val writer = JournalEntryWriter(repository)
                val entryId = writer.create(rating, "") ?: return@launch

                NotificationHelper(appContext).showRatingSavedPrompt(
                    entryId = entryId.toInt(),
                    rating = rating,
                    privacy = privacy
                )
            } catch (_: Exception) {
                // If the local write unexpectedly fails, restore the prompt rather than leaving a
                // permanent "Saving" notification. No journal content is written to logs.
                NotificationHelper(appContext).showMoodPrompt(
                    effectivePrivacy,
                    effectiveTimeoutMinutes
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleNote(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val postedPrivacy = privacyEmbeddedInNotification(intent)
        val postedTimeoutMinutes = timeoutEmbeddedInNotification(intent)
        if (interactionBlockedWhileLocked(appContext, postedPrivacy)) {
            restoreBlockedPrompt(appContext, postedPrivacy, postedTimeoutMinutes)
            return
        }

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()

        val entryId = intent.getIntExtra(EXTRA_ENTRY_ID, NO_ENTRY_ID)

        // An empty reply adds nothing. If a rating was already saved, dismiss the short-lived
        // follow-up; otherwise leave the original prompt alone.
        if (replyText.isNullOrEmpty()) {
            if (entryId != NO_ENTRY_ID) {
                NotificationManagerCompat.from(context).cancel(
                    NotificationHelper.NOTIFICATION_PROMPT_ID
                )
            }
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            var effectivePrivacy = postedPrivacy
            var effectiveTimeoutMinutes = postedTimeoutMinutes
            try {
                val settingsManager = SettingsManager(appContext)
                val privacy = settingsManager.privacySettingsFlow.first()
                val timeoutMinutes = settingsManager.settingsFlow.first().checkInNotificationTimeoutMinutes
                effectivePrivacy = privacy
                effectiveTimeoutMinutes = timeoutMinutes
                if (interactionBlockedWhileLocked(appContext, privacy)) {
                    restoreBlockedPrompt(appContext, privacy, timeoutMinutes)
                    return@launch
                }

                val db = MoodDatabase.getDatabase(appContext)
                val repository = MoodRepository(db.moodDao(), ManualBackupManager(appContext))
                val writer = JournalEntryWriter(repository)
                if (entryId != NO_ENTRY_ID) {
                    if (!writer.attachContent(entryId, replyText)) {
                        // Defensive fallback: preserve submitted text if the referenced rating disappeared.
                        writer.create(null, replyText)
                    }
                } else {
                    // Note-only entries are intentional and excluded from mood calculations.
                    writer.create(null, replyText)
                }

                NotificationManagerCompat.from(appContext).cancel(
                    NotificationHelper.NOTIFICATION_PROMPT_ID
                )
            } catch (_: Exception) {
                // A submitted note should never fail silently. Re-post a prompt using the newest
                // privacy setting we successfully loaded so the user has an obvious retry path.
                NotificationHelper(appContext).showMoodPrompt(
                    effectivePrivacy,
                    effectiveTimeoutMinutes
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun privacyEmbeddedInNotification(intent: Intent) = PrivacyPresets.legacyAlpha1.copy(
        checkInLockScreenPrivacy = intent
            .getStringExtra(NotificationHelper.EXTRA_CHECKIN_LOCKSCREEN_PRIVACY)
            ?.let { name -> CheckInLockScreenPrivacy.entries.firstOrNull { it.name == name } }
            ?: PrivacyPresets.legacyAlpha1.checkInLockScreenPrivacy,
        showCheckInsOnConnectedDevices = if (
            intent.hasExtra(NotificationHelper.EXTRA_SHOW_CHECKINS_CONNECTED)
        ) {
            intent.getBooleanExtra(NotificationHelper.EXTRA_SHOW_CHECKINS_CONNECTED, true)
        } else {
            PrivacyPresets.legacyAlpha1.showCheckInsOnConnectedDevices
        }
    )

    private fun timeoutEmbeddedInNotification(intent: Intent): Int =
        intent.getIntExtra(NotificationHelper.EXTRA_CHECKIN_TIMEOUT_MINUTES, 0).coerceAtLeast(0)

    private fun interactionBlockedWhileLocked(
        context: Context,
        privacy: com.nickspeelman.localjournal.data.PrivacySettings
    ): Boolean {
        if (privacy.checkInLockScreenPrivacy == CheckInLockScreenPrivacy.FULL) return false
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguard.isDeviceLocked
        } else {
            @Suppress("DEPRECATION")
            keyguard.isKeyguardLocked
        }
    }

    private fun restoreBlockedPrompt(
        context: Context,
        privacy: com.nickspeelman.localjournal.data.PrivacySettings,
        timeoutMinutes: Int
    ) {
        if (privacy.checkInLockScreenPrivacy == CheckInLockScreenPrivacy.PRIVATE) {
            NotificationHelper(context).showMoodPrompt(privacy, timeoutMinutes)
        }
        // Hidden already has a SECRET notification in place; do not replace it with anything that
        // could become visible while the device remains locked.
    }

    /**
     * Preserve compatibility with a notification posted by an older installed build immediately
     * before the app was upgraded. Those PendingIntents have no explicit action and still encode
     * rating + note in one RemoteInput string.
     */
    private fun handleLegacyReply(context: Context, intent: Intent) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
            ?.toString()

        if (replyText.isNullOrBlank()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            var privacy = PrivacyPresets.legacyAlpha1
            var timeoutMinutes = 0
            try {
                val settingsManager = SettingsManager(appContext)
                privacy = settingsManager.privacySettingsFlow.first()
                timeoutMinutes = settingsManager.settingsFlow.first().checkInNotificationTimeoutMinutes
                if (interactionBlockedWhileLocked(appContext, privacy)) {
                    restoreBlockedPrompt(appContext, privacy, timeoutMinutes)
                    return@launch
                }

                val db = MoodDatabase.getDatabase(appContext)
                val repository = MoodRepository(db.moodDao(), ManualBackupManager(appContext))
                val writer = JournalEntryWriter(repository)
                writer.createCompact(replyText)

                NotificationManagerCompat.from(appContext).cancel(
                    NotificationHelper.NOTIFICATION_PROMPT_ID
                )
            } catch (_: Exception) {
                NotificationHelper(appContext).showMoodPrompt(privacy, timeoutMinutes)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_RATING =
            "com.nickspeelman.aside.action.SET_NOTIFICATION_RATING"
        const val ACTION_ADD_NOTE =
            "com.nickspeelman.aside.action.ADD_NOTIFICATION_NOTE"
        const val EXTRA_RATING = "notification_rating"
        const val EXTRA_ENTRY_ID = "notification_entry_id"
        private const val NO_ENTRY_ID = -1
    }
}
