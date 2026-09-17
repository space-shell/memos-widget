package dev.jamesnicholls.memoswidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import dev.jamesnicholls.memoswidget.AppContainer
import dev.jamesnicholls.memoswidget.MainActivity
import dev.jamesnicholls.memoswidget.QuickComposeActivity
import dev.jamesnicholls.memoswidget.R
import dev.jamesnicholls.memoswidget.data.StoredNote
import dev.jamesnicholls.memoswidget.net.UrlUtil
import dev.jamesnicholls.memoswidget.util.MemoStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fetches widget data (server first, cache as fallback) and re-renders all
 * instances of [MemosWidgetProvider].
 */
class WidgetRefresher(
    private val context: Context,
    private val container: AppContainer,
) {

    fun requestRefresh() {
        WidgetRefreshWorker.enqueue(context)
    }

    /** Re-renders every widget instance from the cached state (no network). */
    suspend fun renderAll() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, MemosWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return

        val settings = container.settingsRepository.settings.first()
        val notes = container.widgetStateRepository.notes.first()
        val counts = container.widgetStateRepository.dailyCounts.first()
        val fetchFailed = container.widgetStateRepository.fetchFailed.first()
        val views = buildRemoteViews(notes, counts, fetchFailed, settings.serverUrl)
        manager.updateAppWidget(ids, views)
        ids.forEach { manager.notifyAppWidgetViewDataChanged(it, R.id.widget_notes_list) }
    }

    suspend fun refreshAll() {
        fetchLatest()
        renderAll()
    }

    /**
     * Called right after a memo is sent: updates the cache optimistically and
     * re-renders immediately, so the new memo is visible without waiting for
     * the server round-trip.
     */
    suspend fun onMemoSent(name: String, content: String) {
        val zone = ZoneId.systemDefault()
        container.widgetStateRepository.prependNote(
            StoredNote(
                name = name,
                content = content,
                createTime = java.time.Instant.now().toString(),
            ),
            todayKey = LocalDate.now(zone).toString(),
        )
        renderAll()
    }

    private suspend fun fetchLatest() {
        val settings = container.settingsRepository.settings.first()
        if (!settings.isConfigured) return
        val startedAt = android.os.SystemClock.elapsedRealtime()
        try {
            val baseUrl = UrlUtil.normaliseBaseUrl(settings.serverUrl)
            val fetched = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                container.memosApi.listRecentMemos(baseUrl, settings.accessToken, limit = RECENT_MEMO_LIMIT)
            }
            if (fetched == null) {
                android.util.Log.w(TAG, "widget fetch timed out after ${FETCH_TIMEOUT_MS}ms")
                container.widgetStateRepository.setFetchFailed(true)
            } else {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                container.widgetStateRepository.updateNotes(
                    MemoStats.todaysMemos(fetched, zone, today),
                )
                container.widgetStateRepository.updateDailyCounts(
                    MemoStats.dailyCounts(fetched, zone, today, days = HeatmapRenderer.DEFAULT_WEEKS * 7),
                )
                container.widgetStateRepository.setFetchFailed(false)
                android.util.Log.i(
                    TAG,
                    "widget fetch ok: ${fetched.size} recent memos in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
                )
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "widget fetch failed: ${e.message}", e)
            // Keep the cached notes on any failure.
            container.widgetStateRepository.setFetchFailed(true)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildRemoteViews(
        notes: List<StoredNote>,
        counts: Map<String, Int>,
        fetchFailed: Boolean,
        serverUrl: String,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_memos)

        views.setViewVisibility(
            R.id.widget_notes_empty,
            if (notes.isEmpty()) View.VISIBLE else View.GONE,
        )

        // Scrollable "today's memos" list.
        views.setRemoteAdapter(
            R.id.widget_notes_list,
            Intent(context, MemosListViewService::class.java),
        )
        views.setEmptyView(R.id.widget_notes_list, R.id.widget_notes_empty)

        // Contribution-style heatmap.
        val density = context.resources.displayMetrics.density
        val heatmap = HeatmapRenderer.render(
            counts = counts,
            today = LocalDate.now(),
            cellPx = 9f * density,
            gapPx = 2f * density,
        )
        views.setImageViewBitmap(R.id.widget_heatmap, heatmap)

        views.setViewVisibility(
            R.id.widget_offline_notice,
            if (fetchFailed) View.VISIBLE else View.GONE,
        )

        val prompts = context.resources.getStringArray(R.array.widget_prompts)
        views.setTextViewText(R.id.widget_prompt, prompts.random())

        views.setOnClickPendingIntent(
            R.id.widget_compose,
            QuickComposeActivity.createLaunchIntent(context),
        )

        val normalisedUrl = UrlUtil.normaliseBaseUrl(serverUrl)
        views.setOnClickPendingIntent(
            R.id.widget_title,
            if (normalisedUrl.isNotEmpty()) {
                browserPendingIntent(normalisedUrl)
            } else {
                settingsPendingIntent()
            },
        )
        return views
    }

    private fun browserPendingIntent(url: String): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_BROWSER,
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun settingsPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SETTINGS,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "MemosWidget"
        private const val FETCH_TIMEOUT_MS = 20_000L
        private const val RECENT_MEMO_LIMIT = 1000
        private const val REQUEST_BROWSER = 2001
        private const val REQUEST_SETTINGS = 2002
    }
}
