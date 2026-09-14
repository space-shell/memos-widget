package dev.jamesnicholls.memoswidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import dev.jamesnicholls.memoswidget.MemosApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MemosWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            refresh(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun refresh(context: Context) {
        val result = goAsync()
        val container = (context.applicationContext as MemosApp).container
        scope.launch {
            try {
                container.widgetRefresher.refreshAll()
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.jamesnicholls.memoswidget.action.REFRESH_WIDGET"
    }
}
