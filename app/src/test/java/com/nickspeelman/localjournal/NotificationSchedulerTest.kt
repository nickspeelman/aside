package com.nickspeelman.localjournal

import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.notifications.NotificationScheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationSchedulerTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `waking window rejects delayed alarm after bedtime`() {
        val settings = UserSettings(
            sleepEndHour = 8,
            sleepEndMinute = 0,
            sleepStartHour = 22,
            sleepStartMinute = 0
        )
        val delayed = ZonedDateTime.of(2026, 10, 6, 22, 15, 0, 0, utc)

        assertFalse(NotificationScheduler.isWithinWakingWindow(delayed.toInstant().toEpochMilli(), settings, utc))
    }

    @Test
    fun `waking window supports bedtime after midnight`() {
        val settings = UserSettings(
            sleepEndHour = 8,
            sleepEndMinute = 0,
            sleepStartHour = 2,
            sleepStartMinute = 0
        )
        val oneAm = ZonedDateTime.of(2026, 10, 6, 1, 0, 0, 0, utc)

        assertTrue(NotificationScheduler.isWithinWakingWindow(oneAm.toInstant().toEpochMilli(), settings, utc))
    }

    private val settings = UserSettings(
        promptsPerDay = 4,
        sleepStartHour = 22, // 10 PM
        sleepStartMinute = 0,
        sleepEndHour = 8, // 8 AM
        sleepEndMinute = 0,
        isPaused = false,
        use24Hour = false
    )

    @Test
    fun `schedule during sleep hours should delay until wake time`() {
        // Current time: 2 AM. First waking window is 8:00-11:30.
        val now = getTime(2, 0)
        val delay = NotificationScheduler.calculateNextDelayMinutes(now, settings)

        assertTrue("Delay should be at least 6 hours", delay >= 6 * 60)
        assertTrue("Delay should stay within the first window", delay < (6 * 60) + 210)
    }

    @Test
    fun `new schedule during waking hours can use remainder of current window`() {
        // Current time: 9 AM, inside first window (8:00-11:30).
        val now = getTime(9, 0)
        val delay = NotificationScheduler.calculateNextDelayMinutes(
            nowMillis = now,
            settings = settings,
            includeCurrentWindow = true
        )

        assertTrue("Delay should be at least one minute", delay >= 1)
        assertTrue("Delay should remain in the current window", delay < 150)
    }

    @Test
    fun `after a prompt fires scheduler advances to next window`() {
        // Current time: 9 AM. Next window is 11:30-15:00.
        val now = getTime(9, 0)
        val delay = NotificationScheduler.calculateNextDelayMinutes(
            nowMillis = now,
            settings = settings,
            includeCurrentWindow = false
        )

        assertTrue("Delay should reach the next window", delay >= 150)
        assertTrue("Delay should stay within the next window", delay < 360)
    }

    @Test
    fun `new schedule in final window can still notify today`() {
        // Current time: 9 PM, final window is 18:30-22:00.
        val now = getTime(21, 0)
        val delay = NotificationScheduler.calculateNextDelayMinutes(
            nowMillis = now,
            settings = settings,
            includeCurrentWindow = true
        )

        assertTrue("Delay should be at least one minute", delay >= 1)
        assertTrue("Delay should be before bedtime", delay < 60)
    }

    @Test
    fun `after prompt in final window scheduler moves to tomorrow`() {
        val now = getTime(21, 0)
        val delay = NotificationScheduler.calculateNextDelayMinutes(
            nowMillis = now,
            settings = settings,
            includeCurrentWindow = false
        )

        // Tomorrow's first window begins 11 hours later at 8 AM.
        assertTrue("Delay should be at least 11 hours", delay >= 11 * 60)
        assertTrue("Delay should remain in tomorrow's first window", delay < (11 * 60) + 210)
    }

    private fun getTime(hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
