package com.novasoftware.hadoorbell.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {
    companion object {
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val STREAM_SOURCE = stringPreferencesKey("stream_source")
        val QUICK_REPLY_ENTITY_ID = stringPreferencesKey("quick_reply_entity_id")
        val LOCK_ENTITY_ID = stringPreferencesKey("lock_entity_id")
        val INSTANT_TWO_WAY_AUDIO = booleanPreferencesKey("instant_two_way_audio")
        val WEBRTC_PROVIDER = stringPreferencesKey("webrtc_provider")
    }

    override val haUrlFlow: Flow<String?> = context.dataStore.data.map { it[HA_URL]?.trim() }
    override val haTokenFlow: Flow<String?> = context.dataStore.data.map { it[HA_TOKEN]?.trim() }
    override val streamSourceFlow: Flow<String?> = context.dataStore.data.map { it[STREAM_SOURCE]?.trim() }
    override val quickReplyEntityIdFlow: Flow<String?> = context.dataStore.data.map { it[QUICK_REPLY_ENTITY_ID]?.trim() }
    override val lockEntityIdFlow: Flow<String?> = context.dataStore.data.map { it[LOCK_ENTITY_ID]?.trim() }
    override val instantTwoWayAudioFlow: Flow<Boolean> = context.dataStore.data.map { it[INSTANT_TWO_WAY_AUDIO] ?: false }
    override val webrtcProviderFlow: Flow<String> = context.dataStore.data.map { it[WEBRTC_PROVIDER] ?: "frigate" }

    override suspend fun saveSettings(url: String, token: String, source: String, quickReplyEntityId: String, lockEntityId: String, instantTwoWayAudio: Boolean, provider: String) {
        context.dataStore.edit { preferences ->
            var finalUrl = url.trim().trimEnd('/')
            if (finalUrl.isNotBlank()) {
                finalUrl = finalUrl.replaceFirst("http://", "https://")
                if (!finalUrl.startsWith("https://")) {
                    finalUrl = "https://$finalUrl"
                }
            }
            preferences[HA_URL] = finalUrl
            preferences[HA_TOKEN] = token.trim()
            preferences[STREAM_SOURCE] = source.trim()
            preferences[QUICK_REPLY_ENTITY_ID] = quickReplyEntityId.trim()
            preferences[LOCK_ENTITY_ID] = lockEntityId.trim()
            preferences[INSTANT_TWO_WAY_AUDIO] = instantTwoWayAudio
            preferences[WEBRTC_PROVIDER] = provider
        }
    }
}
