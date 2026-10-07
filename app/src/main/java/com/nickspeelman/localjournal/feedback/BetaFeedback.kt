package com.nickspeelman.localjournal.feedback

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object BetaFeedback {
    const val ADDRESS = "asidehelp@nickspeelman.com"

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

    fun launch(context: Context) {
        val subject = "Aside beta feedback"
        val body = body(context)
        val mailUri = Uri.parse(
            "mailto:$ADDRESS?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        )
        val mail = Intent(Intent.ACTION_SENDTO, mailUri)
        if (mail.resolveActivity(context.packageManager) != null) {
            context.startActivity(mail)
        } else {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "$subject\n\nTo: $ADDRESS\n\n$body")
            }
            context.startActivity(Intent.createChooser(share, "Send beta feedback"))
        }
    }
}
