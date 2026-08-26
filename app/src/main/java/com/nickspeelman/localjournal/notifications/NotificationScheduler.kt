package com.nickspeelman.localjournal.notifications

import com.nickspeelman.localjournal.data.UserSettings
import java.util.Calendar
import kotlin.math.max
import kotlin.random.Random

object NotificationScheduler {

    /**
     * Calculates the delay in minutes until the next notification.
     *
     * When [includeCurrentWindow] is true, a newly created/replaced schedule may use the
     * remainder of the current waking window. When it is false (after a prompt has just fired),
     * scheduling advances to the following window so a single window cannot generate a burst
     * of multiple prompts.
     */
    fun calculateNextDelayMinutes(
        nowMillis: Long,
        settings: UserSettings,
        includeCurrentWindow: Boolean = true
    ): Long {
        require(settings.promptsPerDay > 0) { "promptsPerDay must be greater than zero" }

        val wakeTime = getTodayTime(nowMillis, settings.sleepEndHour, settings.sleepEndMinute)
        val sleepTime = getTodayTime(nowMillis, settings.sleepStartHour, settings.sleepStartMinute)

        // Supports waking periods that cross midnight, e.g. wake at 08:00 and sleep at 02:00.
        if (!sleepTime.after(wakeTime)) {
            sleepTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        val wakingDurationMillis = sleepTime.timeInMillis - wakeTime.timeInMillis
        val windowDurationMillis = wakingDurationMillis / settings.promptsPerDay
        val timeSinceWake = nowMillis - wakeTime.timeInMillis

        return when {
            timeSinceWake < 0 -> {
                // Before today's wake time: choose a random point in today's first window.
                randomDelayWithinWindow(
                    nowMillis = nowMillis,
                    windowStartMillis = wakeTime.timeInMillis,
                    windowEndMillis = wakeTime.timeInMillis + windowDurationMillis
                )
            }

            nowMillis >= sleepTime.timeInMillis -> {
                // After today's sleep time: choose a random point in tomorrow's first window.
                val tomorrowWakeMillis = wakeTime.cloneCalendar().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                randomDelayWithinWindow(
                    nowMillis = nowMillis,
                    windowStartMillis = tomorrowWakeMillis,
                    windowEndMillis = tomorrowWakeMillis + windowDurationMillis
                )
            }

            else -> {
                val currentWindowIndex = (timeSinceWake / windowDurationMillis).toInt()
                val targetWindowIndex = if (includeCurrentWindow) {
                    currentWindowIndex
                } else {
                    currentWindowIndex + 1
                }

                if (targetWindowIndex >= settings.promptsPerDay) {
                    val tomorrowWakeMillis = wakeTime.cloneCalendar().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }.timeInMillis
                    randomDelayWithinWindow(
                        nowMillis = nowMillis,
                        windowStartMillis = tomorrowWakeMillis,
                        windowEndMillis = tomorrowWakeMillis + windowDurationMillis
                    )
                } else {
                    val targetWindowStart = wakeTime.timeInMillis +
                        (targetWindowIndex * windowDurationMillis)
                    val targetWindowEnd = targetWindowStart + windowDurationMillis

                    randomDelayWithinWindow(
                        nowMillis = nowMillis,
                        windowStartMillis = targetWindowStart,
                        windowEndMillis = targetWindowEnd
                    )
                }
            }
        }
    }

    private fun randomDelayWithinWindow(
        nowMillis: Long,
        windowStartMillis: Long,
        windowEndMillis: Long
    ): Long {
        val earliestMillis = max(nowMillis, windowStartMillis)
        val availableMillis = (windowEndMillis - earliestMillis).coerceAtLeast(0L)

        if (availableMillis <= 0L) {
            return 1L
        }

        val randomOffsetMillis = Random.nextLong(availableMillis)
        val delayMillis = (earliestMillis - nowMillis) + randomOffsetMillis

        // WorkManager accepts a zero delay, but keeping at least one minute here avoids an
        // unexpected prompt appearing immediately just because the app was opened.
        return (delayMillis / MILLIS_PER_MINUTE).coerceAtLeast(1L)
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

    private fun Calendar.cloneCalendar(): Calendar = clone() as Calendar

    private const val MILLIS_PER_MINUTE = 60_000L
}
