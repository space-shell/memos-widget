package dev.jamesnicholls.memoswidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val DEFAULT_VISIBILITY = stringPreferencesKey("default_visibility")
    }

    val settings: Flow<MemosSettings> = context.settingsDataStore.data.map { prefs ->
        MemosSettings(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            accessToken = prefs[Keys.ACCESS_TOKEN] ?: "",
            defaultVisibility = MemoVisibility.fromWireName(
                prefs[Keys.DEFAULT_VISIBILITY] ?: MemoVisibility.PRIVATE.wireName
            ),
        )
    }

    suspend fun updateServerUrl(value: String) {
        context.settingsDataStore.edit { it[Keys.SERVER_URL] = value.trim() }
    }

    suspend fun updateAccessToken(value: String) {
        context.settingsDataStore.edit { it[Keys.ACCESS_TOKEN] = value.trim() }
    }

    suspend fun updateDefaultVisibility(value: MemoVisibility) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_VISIBILITY] = value.wireName }
    }
}
