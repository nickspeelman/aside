package com.nickspeelman.localjournal.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.nickspeelman.localjournal.data.SettingsManager
import com.nickspeelman.localjournal.data.UserSettings
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Schedules the next random mood prompt with AlarmManager rather than WorkManager.
 *
 * These prompts are user-facing reminders. WorkManager can defer delayed work for an
 * unbounded amount of time under Doze / system scheduling. AlarmManager's inexact,
 * allow-while-idle alarm is a better fit: the prompt may still be batched by Android,
 * but the alarm can wake the app to post the notification while the phone is idle.
 */
object RandomPromptAlarmScheduler {
    private const val TAG = "RandomPromptAlarm"
    private const val PREFS_NAME = "random_prompt_alarm"
    private const val KEY_NEXT_TRIGGER_AT = "next_trigger_at"
    private const val REQUEST_CODE = 4101
    private const val LEGACY_WORK_NAME = "random_prompt_unique"

    /**
     * Called on ordinary app startup.
     *
     * If a future target was already chosen, re-register that same target instead of
     * choosing a new one. This makes app launches repair a missing alarm without pushing
     * the next prompt farther into the future.
     */
    suspend fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        cancelLegacyWorkManagerSchedule(appContext)
        val settings = SettingsManager(appContext).settingsFlow.first()

        if (settings.isPaused) {
            cancel(appContext)
            return
        }

        val now = System.currentTimeMillis()
        val savedTrigger = prefs(appContext).getLong(KEY_NEXT_TRIGGER_AT, 0L)

        if (savedTrigger > now) {
            registerAlarm(appContext, savedTrigger)
            Log.i(TAG, "Restored existing random prompt alarm for $savedTrigger")
        } else {
            scheduleFresh(appContext, settings, includeCurrentWindow = true)
        }
    }

    /** Called after the user changes prompt frequency, sleep hours, or pause state. */
    suspend fun reschedule(context: Context) {
        val appContext = context.applicationContext
        cancelLegacyWorkManagerSchedule(appContext)
        val settings = SettingsManager(appContext).settingsFlow.first()

        reschedule(appContext, settings)
    }

    fun reschedule(context: Context, settings: UserSettings) {
        val appContext = context.applicationContext

        if (settings.isPaused) {
            cancel(appContext)
        } else {
            scheduleFresh(appContext, settings, includeCurrentWindow = true)
        }
    }

    /** Called after a random prompt fires so the following waking window gets one prompt. */
    fun scheduleAfterPrompt(context: Context, settings: UserSettings) {
        val appContext = context.applicationContext

        if (settings.isPaused) {
            cancel(appContext)
        } else {
            scheduleFresh(appContext, settings, includeCurrentWindow = false)
        }
    }

    fun schedulingInputsChanged(old: UserSettings, new: UserSettings): Boolean =
        old.promptsPerDay != new.promptsPerDay ||
            old.sleepStartHour != new.sleepStartHour ||
            old.sleepStartMinute != new.sleepStartMinute ||
            old.sleepEndHour != new.sleepEndHour ||
            old.sleepEndMinute != new.sleepEndMinute ||
            old.isPaused != new.isPaused

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(appContext))
        prefs(appContext).edit().remove(KEY_NEXT_TRIGGER_AT).apply()
        Log.i(TAG, "Random prompt alarm cancelled")
    }

    fun nextScheduledAtMillis(context: Context): Long? {
        val value = prefs(context.applicationContext).getLong(KEY_NEXT_TRIGGER_AT, 0L)
        return value.takeIf { it > 0L }
    }

    private fun scheduleFresh(
        context: Context,
        settings: UserSettings,
        includeCurrentWindow: Boolean
    ) {
        val now = System.currentTimeMillis()
        val delayMinutes = NotificationScheduler.calculateNextDelayMinutes(
            nowMillis = now,
            settings = settings,
            includeCurrentWindow = includeCurrentWindow
        )
        val triggerAt = now + TimeUnit.MINUTES.toMillis(delayMinutes)

        prefs(context).edit().putLong(KEY_NEXT_TRIGGER_AT, triggerAt).apply()
        registerAlarm(context, triggerAt)
        Log.i(TAG, "Scheduled random prompt for $triggerAt (in $delayMinutes minutes)")
    }

    private fun registerAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // This is intentionally INEXACT. Random mood prompts do not need exact-alarm special
        // access, but they should still be able to arrive while the phone is idle.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(context)
        )
    }

    private fun cancelLegacyWorkManagerSchedule(context: Context) {
        // v1.0.1 and earlier used this unique WorkManager chain. Cancel it during migration
        // so an already-enqueued legacy worker cannot later create duplicate prompts.
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RandomPromptAlarmReceiver::class.java).apply {
            action = RandomPromptAlarmReceiver.ACTION_RANDOM_PROMPT
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
