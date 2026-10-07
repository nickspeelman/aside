package com.nickspeelman.localjournal.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nickspeelman.localjournal.workers.TestNotificationWorker
import java.util.concurrent.TimeUnit

enum class TestNotificationType {
    CHECK_IN,
    WEEKLY_REPORT,
    MONTHLY_REPORT
}

object TestNotificationScheduler {
    const val DEFAULT_DELAY_SECONDS = 10L

    fun schedule(
        context: Context,
        type: TestNotificationType,
        delaySeconds: Long = DEFAULT_DELAY_SECONDS
    ) {
        val input = Data.Builder()
            .putString(TestNotificationWorker.KEY_TEST_TYPE, type.name)
            .build()

        val request = OneTimeWorkRequestBuilder<TestNotificationWorker>()
            .setInputData(input)
            .setInitialDelay(delaySeconds.coerceAtLeast(0L), TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "aside_test_notification_${type.name.lowercase()}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
