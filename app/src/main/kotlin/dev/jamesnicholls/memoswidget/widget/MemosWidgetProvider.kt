package dev.jamesnicholls.memoswidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.jamesnicholls.memoswidget.QuickComposeActivity
import dev.jamesnicholls.memoswidget.R

class MemosWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_memos)

            views.setOnClickPendingIntent(
                R.id.widget_field,
                QuickComposeActivity.createLaunchIntent(context, startVoice = false),
            )
            views.setOnClickPendingIntent(
                R.id.widget_send,
                QuickComposeActivity.createLaunchIntent(context, startVoice = false),
            )
            views.setOnClickPendingIntent(
                R.id.widget_mic,
                QuickComposeActivity.createLaunchIntent(context, startVoice = true),
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
