package com.nickspeelman.localjournal.security

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.nickspeelman.localjournal.data.PrivacySettings

fun applyAsideWindowPrivacy(activity: ComponentActivity, privacy: PrivacySettings) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.setRecentsScreenshotEnabled(!privacy.hideJournalInRecents)
    }
    val secure = privacy.preventScreenCapture ||
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && privacy.hideJournalInRecents)
    if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}
