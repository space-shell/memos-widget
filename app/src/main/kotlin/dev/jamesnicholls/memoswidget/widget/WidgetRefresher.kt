package dev.jamesnicholls.memoswidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dev.jamesnicholls.memoswidget.AppContainer
import dev.jamesnicholls.memoswidget.QuickComposeActivity
import dev.jamesnicholls.memoswidget.R
import dev.jamesnicholls.memoswidget.data.StoredNote
import dev.jamesnicholls.memoswidget.net.UrlUtil
import dev.jamesnicholls.memoswidget.util.TimeAgo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fetches the latest memos for the widget (server first, cache as fallback)
 * and re-renders all instances of [MemosWidgetProvider].
 */
class WidgetRefresher(
    private val context: Context,
    private val container: AppContainer,
) {

    fun requestRefresh() {
        val intent = Intent(context, MemosWidgetProvider::class.java)
            .setAction(MemosWidgetProvider.ACTION_REFRESH)
        context.sendBroadcast(intent)
    }

    suspend fun refreshAll() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, MemosWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return

        fetchLatest()
        renderAll()
    }

    /**
     * Called right after a memo is sent: updates the cache optimistically and
     * re-renders immediately, so the new memo is visible without waiting for
     * the server round-trip.
     */
    suspend fun onMemoSent(name: String, content: String) {
        container.widgetStateRepository.prependNote(
            StoredNote(
                name = name,
                content = content,
                createTime = java.time.Instant.now().toString(),
            ),
        )
        renderAll()
    }

    private suspend fun renderAll() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, MemosWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return

        val notes = container.widgetStateRepository.notes.first()
        val fetchFailed = container.widgetStateRepository.fetchFailed.first()
        val views = buildRemoteViews(notes, fetchFailed)
        manager.updateAppWidget(ids, views)
    }

    private suspend fun fetchLatest() {
        val settings = container.settingsRepository.settings.first()
        if (!settings.isConfigured) return
        try {
            val baseUrl = UrlUtil.normaliseBaseUrl(settings.serverUrl)
            // Bound the fetch so the goAsync broadcast window is not exceeded.
            val fetched = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                container.memosApi.listRecentMemos(baseUrl, settings.accessToken, limit = 3)
            }
            if (fetched == null) {
                container.widgetStateRepository.setFetchFailed(true)
            } else {
                container.widgetStateRepository.updateNotes(fetched)
                container.widgetStateRepository.setFetchFailed(false)
            }
        } catch (_: Exception) {
            // Keep the cached notes on any failure.
            container.widgetStateRepository.setFetchFailed(true)
        }
    }

    private fun buildRemoteViews(notes: List<StoredNote>, fetchFailed: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_memos)

        val rowIds = intArrayOf(
            R.id.widget_note_row_1,
            R.id.widget_note_row_2,
            R.id.widget_note_row_3,
        )
        val textIds = intArrayOf(
            R.id.widget_note_text_1,
            R.id.widget_note_text_2,
            R.id.widget_note_text_3,
        )
        val timeIds = intArrayOf(
            R.id.widget_note_time_1,
            R.id.widget_note_time_2,
            R.id.widget_note_time_3,
        )
        val dividerIds = intArrayOf(R.id.widget_divider_1, R.id.widget_divider_2)

        rowIds.forEachIndexed { index, rowId ->
            val note = notes.getOrNull(index)
            if (note == null) {
                views.setViewVisibility(rowId, View.GONE)
            } else {
                views.setViewVisibility(rowId, View.VISIBLE)
                views.setTextViewText(textIds[index], note.snippet())
                val timeLabel = TimeAgo.format(note.createTime)
                if (timeLabel.isEmpty()) {
                    views.setViewVisibility(timeIds[index], View.GONE)
                } else {
                    views.setViewVisibility(timeIds[index], View.VISIBLE)
                    views.setTextViewText(timeIds[index], timeLabel)
                }
            }
        }
        dividerIds.forEachIndexed { index, dividerId ->
            views.setViewVisibility(
                dividerId,
                if (notes.size > index + 1) View.VISIBLE else View.GONE,
            )
        }
        views.setViewVisibility(
            R.id.widget_notes_empty,
            if (notes.isEmpty()) View.VISIBLE else View.GONE,
        )

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
        return views
    }

    private fun StoredNote.snippet(): String =
        content
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
            .ifEmpty { "—" }

    companion object {
        private const val FETCH_TIMEOUT_MS = 8_000L
    }
}
