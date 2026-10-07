package com.nickspeelman.localjournal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nickspeelman.localjournal.R
import com.nickspeelman.localjournal.quickentry.QuickEntryActivity

class AsideWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val intent = Intent(context, QuickEntryActivity::class.java).apply {
                // Keep widget capture in its own disposable task so finishing it returns
                // to the launcher instead of revealing an existing Aside task.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_aside_check_in).apply {
                setOnClickPendingIntent(R.id.widget_root, pending)
                setContentDescription(R.id.widget_root, context.getString(R.string.widget_accessibility))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
