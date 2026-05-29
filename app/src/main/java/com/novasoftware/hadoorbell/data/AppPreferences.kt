package com.novasoftware.hadoorbell.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {
    companion object {
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val STREAM_SOURCE = stringPreferencesKey("stream_source")
        val QUICK_REPLY_ENTITY_ID = stringPreferencesKey("quick_reply_entity_id")
        val LOCK_ENTITY_ID = stringPreferencesKey("lock_entity_id")
    }

    val haUrlFlow: Flow<String?> = context.dataStore.data.map { it[HA_URL]?.trim() }
    val haTokenFlow: Flow<String?> = context.dataStore.data.map { it[HA_TOKEN]?.trim() }
    val streamSourceFlow: Flow<String?> = context.dataStore.data.map { it[STREAM_SOURCE]?.trim() }
    val quickReplyEntityIdFlow: Flow<String?> = context.dataStore.data.map { it[QUICK_REPLY_ENTITY_ID]?.trim() }
    val lockEntityIdFlow: Flow<String?> = context.dataStore.data.map { it[LOCK_ENTITY_ID]?.trim() }

    suspend fun saveSettings(url: String, token: String, source: String, quickReplyEntityId: String, lockEntityId: String) {
        context.dataStore.edit { preferences ->
            var finalUrl = url.trim().trimEnd('/')
            if (finalUrl.isNotBlank() && !finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                finalUrl = "https://$finalUrl"
            }
            preferences[HA_URL] = finalUrl
            preferences[HA_TOKEN] = token.trim()
            preferences[STREAM_SOURCE] = source.trim()
            preferences[QUICK_REPLY_ENTITY_ID] = quickReplyEntityId.trim()
            preferences[LOCK_ENTITY_ID] = lockEntityId.trim()
        }
    }
}
