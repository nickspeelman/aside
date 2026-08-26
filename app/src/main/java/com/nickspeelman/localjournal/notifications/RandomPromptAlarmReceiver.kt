package com.nickspeelman.localjournal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives both random-prompt alarms and system events that require alarms to be restored. */
class RandomPromptAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_RANDOM_PROMPT -> {
                        // Posting the notification is quick and completely local.
                        NotificationHelper(appContext).showMoodPrompt()
                        RandomPromptAlarmScheduler.scheduleAfterPrompt(appContext)
                    }

                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        // AlarmManager alarms are cleared by reboot, and clock/time-zone changes
                        // can invalidate the intended waking-window time. Rebuild the schedule.
                        RandomPromptAlarmScheduler.reschedule(appContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RANDOM_PROMPT =
            "com.nickspeelman.localjournal.action.RANDOM_PROMPT"
    }
}
