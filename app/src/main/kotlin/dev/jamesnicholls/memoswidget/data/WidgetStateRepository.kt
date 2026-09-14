package dev.jamesnicholls.memoswidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jamesnicholls.memoswidget.net.MemoSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_state")

@Serializable
data class StoredNote(
    val name: String,
    val content: String,
    val createTime: String? = null,
)

/**
 * Last-known recent memos for home screen widget rendering.
 * Written after successful server fetches so the widget can render
 * instantly (and offline) from cache.
 */
class WidgetStateRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val notes: Flow<List<StoredNote>> = context.widgetDataStore.data.map { prefs ->
        val raw = prefs[KEY_NOTES] ?: return@map emptyList()
        runCatching {
            json.decodeFromString<List<StoredNote>>(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun updateNotes(memos: List<MemoSummary>) {
        val stored = memos.map { StoredNote(name = it.name, content = it.content, createTime = it.createTime) }
        context.widgetDataStore.edit { prefs ->
            prefs[KEY_NOTES] = json.encodeToString(stored)
        }
    }

    val fetchFailed: Flow<Boolean> = context.widgetDataStore.data.map { prefs ->
        prefs[KEY_FETCH_FAILED] ?: false
    }

    suspend fun setFetchFailed(value: Boolean) {
        context.widgetDataStore.edit { prefs ->
            prefs[KEY_FETCH_FAILED] = value
        }
    }

    companion object {
        private val KEY_NOTES = stringPreferencesKey("notes_json")
        private val KEY_FETCH_FAILED = booleanPreferencesKey("fetch_failed")
    }
}
