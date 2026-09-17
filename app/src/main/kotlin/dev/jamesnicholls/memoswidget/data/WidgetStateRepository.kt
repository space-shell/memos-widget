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
 * Last-known widget data: today's memos (for the scrollable list) and
 * per-day counts (for the heatmap). Written after successful server
 * fetches so the widget can render instantly (and offline) from cache.
 */
class WidgetStateRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val notes: Flow<List<StoredNote>> = context.widgetDataStore.data.map { prefs ->
        decodeNotes(prefs[KEY_NOTES])
    }

    val dailyCounts: Flow<Map<String, Int>> = context.widgetDataStore.data.map { prefs ->
        val raw = prefs[KEY_COUNTS] ?: return@map emptyMap()
        runCatching {
            json.decodeFromString<Map<String, Int>>(raw)
        }.getOrDefault(emptyMap())
    }

    suspend fun updateNotes(memos: List<MemoSummary>) {
        val stored = memos.map { StoredNote(name = it.name, content = it.content, createTime = it.createTime) }
        context.widgetDataStore.edit { prefs ->
            prefs[KEY_NOTES] = json.encodeToString(stored)
        }
    }

    suspend fun updateDailyCounts(counts: Map<String, Int>) {
        context.widgetDataStore.edit { prefs ->
            prefs[KEY_COUNTS] = json.encodeToString(counts)
        }
    }

    /**
     * Prepends a just-sent memo (today's list + heatmap count) so the widget
     * reflects it immediately, before the server fetch reconciles the data.
     */
    suspend fun prependNote(note: StoredNote, todayKey: String) {
        context.widgetDataStore.edit { prefs ->
            val current = decodeNotes(prefs[KEY_NOTES])
            prefs[KEY_NOTES] = json.encodeToString((listOf(note) + current).take(MAX_DAY_NOTES))
            val counts = prefs[KEY_COUNTS]
                ?.let { raw -> runCatching { json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap()) }
                .orEmpty()
            prefs[KEY_COUNTS] = json.encodeToString(counts + (todayKey to (counts[todayKey] ?: 0) + 1))
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

    private fun decodeNotes(raw: String?): List<StoredNote> {
        if (raw == null) return emptyList()
        return runCatching { json.decodeFromString<List<StoredNote>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val KEY_NOTES = stringPreferencesKey("notes_json")
        private val KEY_COUNTS = stringPreferencesKey("counts_json")
        private val KEY_FETCH_FAILED = booleanPreferencesKey("fetch_failed")
        private const val MAX_DAY_NOTES = 50
    }
}
