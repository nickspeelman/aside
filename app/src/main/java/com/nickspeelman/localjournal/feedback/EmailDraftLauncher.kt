package com.nickspeelman.localjournal.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object EmailDraftLauncher {
    fun launch(context: Context, address: String, subject: String, body: String): Boolean {
        val mailUri = Uri.parse("mailto:$address")
        val intent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
