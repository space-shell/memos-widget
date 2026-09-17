package dev.jamesnicholls.memoswidget.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.jamesnicholls.memoswidget.MemosApp
import dev.jamesnicholls.memoswidget.R
import dev.jamesnicholls.memoswidget.data.StoredNote
import dev.jamesnicholls.memoswidget.util.TimeAgo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Feeds the widget's scrollable "today's memos" ListView.
 */
class MemosListViewService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NotesViewsFactory(applicationContext as MemosApp)

    private class NotesViewsFactory(
        private val app: MemosApp,
    ) : RemoteViewsService.RemoteViewsFactory {

        private var notes: List<StoredNote> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val repository = app.container.widgetStateRepository
            notes = runBlocking { repository.notes.first() }
        }

        override fun onDestroy() = Unit

        override fun getCount(): Int = notes.size

        override fun getViewAt(position: Int): RemoteViews {
            val note = notes.getOrNull(position) ?: return RemoteViews(app.packageName, R.layout.widget_note_item)
            val views = RemoteViews(app.packageName, R.layout.widget_note_item)
            views.setTextViewText(R.id.note_item_text, note.snippet())
            val timeLabel = TimeAgo.format(note.createTime)
            if (timeLabel.isEmpty()) {
                views.setViewVisibility(R.id.note_item_time, android.view.View.GONE)
            } else {
                views.setViewVisibility(R.id.note_item_time, android.view.View.VISIBLE)
                views.setTextViewText(R.id.note_item_time, timeLabel)
            }
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            notes.getOrNull(position)?.name?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}

private fun StoredNote.snippet(): String =
    content
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
        .ifEmpty { "—" }
