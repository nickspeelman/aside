package com.nickspeelman.localjournal.feedback

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object BetaFeedback {
    const val ADDRESS = "nick@nickspeelman.com"

    fun body(context: Context): String {
        val notifications = if (NotificationManagerCompat.from(context).areNotificationsEnabled()) "Yes" else "No"
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
        val buildType = if (
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        ) "debug" else "release"

        return """What happened?


---

Technical information:
Aside $versionName ($versionCode)
Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Build: $buildType
Notifications permitted: $notifications
""".trimEnd()
    }

    fun launch(context: Context): Boolean {
        val subject = "Aside beta feedback"
        val body = body(context)
        return EmailDraftLauncher.launch(context, ADDRESS, subject, body)
    }
}
