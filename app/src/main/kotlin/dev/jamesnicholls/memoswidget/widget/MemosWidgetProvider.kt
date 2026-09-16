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
        // Cache-only render: fast, no network. Network work goes to WorkManager
        // so OEM process freezers cannot kill it mid-request.
        renderFromCache(context)
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            renderFromCache(context)
            WidgetRefreshWorker.enqueue(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun renderFromCache(context: Context) {
        val result = goAsync()
        val container = (context.applicationContext as MemosApp).container
        scope.launch {
            try {
                container.widgetRefresher.renderAll()
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.jamesnicholls.memoswidget.action.REFRESH_WIDGET"
    }
}
