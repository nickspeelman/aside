package com.nickspeelman.localjournal

import com.nickspeelman.localjournal.data.UserSettings
import com.nickspeelman.localjournal.notifications.NotificationScheduler
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

class NotificationSchedulerTest {

    private val settings = UserSettings(
        promptsPerDay = 4,
        sleepStartHour = 22, // 10 PM
        sleepStartMinute = 0,
        sleepEndHour = 8, // 8 AM
        sleepEndMinute = 0
    )

    @Test
    fun `schedule during sleep hours should delay until wake time`() {
        // Current time: 2 AM
        val now = getTime(2, 0)
        val delay = NotificationScheduler.calculateNextDelayMinutes(now, settings)
        
        // Expected: Should be at least 6 hours away (until 8 AM)
        assertTrue("Delay should be at least 6 hours", delay >= 6 * 60)
        // And no more than 6 hours + window duration (14 hours / 4 = 3.5 hours)
        assertTrue("Delay should be within the first window", delay <= (6 * 60) + 210)
    }

    @Test
    fun `schedule during waking hours should pick next window`() {
        // Waking: 8 AM - 10 PM (14 hours)
        // Windows: 8-11:30, 11:30-15:00, 15:00-18:30, 18:30-22:00
        
        // Current time: 9 AM (first window)
        val now = getTime(9, 0)
        val delay = NotificationScheduler.calculateNextDelayMinutes(now, settings)
        
        // Expected: Should be scheduled for the next window starting at 11:30
        // Delay should be at least 2.5 hours (until 11:30)
        assertTrue("Delay should be at least 2.5 hours", delay >= 2.5 * 60)
        // And no more than 2.5 hours + 3.5 hours (the next window duration)
        assertTrue("Delay should be within the second window", delay <= (2.5 * 60) + 210)
    }

    @Test
    fun `schedule after last window should delay until next day wake time`() {
        // Current time: 9 PM (after last window starts at 18:30)
        // Or if we define "after last window starts", the logic picks tomorrow.
        // My logic says: if nextWindowIndex >= promptsPerDay -> Tomorrow.
        // 9 PM is in the last window (index 3). Next is index 4.
        
        val now = getTime(21, 0)
        val delay = NotificationScheduler.calculateNextDelayMinutes(now, settings)
        
        // Expected: Tomorrow 8 AM. Delay = 3 hours (to midnight) + 8 hours = 11 hours
        assertTrue("Delay should be at least 11 hours", delay >= 11 * 60)
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
