package com.nickspeelman.localjournal.notifications

import com.nickspeelman.localjournal.data.UserSettings
import java.util.*
import kotlin.random.Random

object NotificationScheduler {

    /**
     * Calculates the delay in minutes until the next notification.
     */
    fun calculateNextDelayMinutes(nowMillis: Long, settings: UserSettings): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        
        val wakeTime = getTodayTime(nowMillis, settings.sleepEndHour, settings.sleepEndMinute)
        val sleepTime = getTodayTime(nowMillis, settings.sleepStartHour, settings.sleepStartMinute)

        // If sleep start is before wake time (e.g. sleep 2am, wake 8am), adjust sleep to next day
        if (sleepTime.before(wakeTime)) {
            sleepTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        val wakingDurationMillis = sleepTime.timeInMillis - wakeTime.timeInMillis
        val windowDurationMillis = wakingDurationMillis / settings.promptsPerDay

        // Find which window we are currently in
        val timeSinceWake = nowMillis - wakeTime.timeInMillis
        
        return if (timeSinceWake < 0) {
            // It's currently before today's wake time (middle of the night)
            // Schedule in the first window of today
            val firstWindowStart = wakeTime.timeInMillis
            val delay = firstWindowStart - nowMillis
            (delay / (60 * 1000)) + Random.nextLong(0, windowDurationMillis / (60 * 1000))
        } else if (nowMillis >= sleepTime.timeInMillis) {
            // It's after today's sleep time
            // Schedule for tomorrow's first window
            val tomorrowWake = wakeTime.apply { add(Calendar.DAY_OF_YEAR, 1) }
            val delay = tomorrowWake.timeInMillis - nowMillis
            (delay / (60 * 1000)) + Random.nextLong(0, windowDurationMillis / (60 * 1000))
        } else {
            // We are in the waking period. Find the next window.
            val currentWindowIndex = (timeSinceWake / windowDurationMillis).toInt()
            val nextWindowIndex = currentWindowIndex + 1
            
            if (nextWindowIndex >= settings.promptsPerDay) {
                // No more windows today, schedule for tomorrow
                val tomorrowWake = wakeTime.apply { add(Calendar.DAY_OF_YEAR, 1) }
                val delay = tomorrowWake.timeInMillis - nowMillis
                (delay / (60 * 1000)) + Random.nextLong(0, windowDurationMillis / (60 * 1000))
            } else {
                // Schedule in the next window
                val nextWindowStart = wakeTime.timeInMillis + (nextWindowIndex * windowDurationMillis)
                val delay = nextWindowStart - nowMillis
                (delay / (60 * 1000)) + Random.nextLong(0, windowDurationMillis / (60 * 1000))
            }
        }
    }

    private fun getTodayTime(nowMillis: Long, hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
