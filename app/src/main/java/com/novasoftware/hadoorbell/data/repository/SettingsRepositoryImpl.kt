package com.novasoftware.hadoorbell.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.model.normalizeHomeAssistantUrl
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(StreamButtonLayoutMigration) }
)

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {
    companion object {
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val STREAM_SOURCE = stringPreferencesKey("stream_source")
        val INSTANT_TWO_WAY_AUDIO = booleanPreferencesKey("instant_two_way_audio")
        val WEBRTC_PROVIDER = stringPreferencesKey("webrtc_provider")
        val ANSWER_ACTION_SERVICE = stringPreferencesKey("answer_action_service")
        val ANSWER_ACTION_ENTITY_ID = stringPreferencesKey("answer_action_entity_id")
        val HANGUP_ACTION_SERVICE = stringPreferencesKey("hangup_action_service")
        val HANGUP_ACTION_ENTITY_ID = stringPreferencesKey("hangup_action_entity_id")
    }

    override val haUrlFlow: Flow<String?> = context.dataStore.data.map { it[HA_URL]?.trim() }
    override val haTokenFlow: Flow<String?> = context.dataStore.data.map { it[HA_TOKEN]?.trim() }
    override val streamSourceFlow: Flow<String?> = context.dataStore.data.map { it[STREAM_SOURCE]?.trim() }
    override val instantTwoWayAudioFlow: Flow<Boolean> = context.dataStore.data.map { it[INSTANT_TWO_WAY_AUDIO] ?: false }
    override val webrtcProviderFlow: Flow<String> = context.dataStore.data.map { it[WEBRTC_PROVIDER] ?: "frigate" }
    override val answerActionServiceFlow: Flow<String?> = context.dataStore.data.map { it[ANSWER_ACTION_SERVICE]?.trim() }
    override val answerActionEntityIdFlow: Flow<String?> = context.dataStore.data.map { it[ANSWER_ACTION_ENTITY_ID]?.trim() }
    override val hangupActionServiceFlow: Flow<String?> = context.dataStore.data.map { it[HANGUP_ACTION_SERVICE]?.trim() }
    override val hangupActionEntityIdFlow: Flow<String?> = context.dataStore.data.map { it[HANGUP_ACTION_ENTITY_ID]?.trim() }
    override val streamButtonLayoutFlow: Flow<StreamButtonLayout> = context.dataStore.data.map {
        it[STREAM_BUTTON_LAYOUT]?.let(StreamButtonLayoutJson::decode) ?: StreamButtonLayout()
    }

    override suspend fun saveSettings(
        url: String,
        token: String,
        source: String,
        instantTwoWayAudio: Boolean,
        provider: String,
        answerActionService: String,
        answerActionEntityId: String,
        hangupActionService: String,
        hangupActionEntityId: String,
        streamButtonLayout: StreamButtonLayout
    ) {
        context.dataStore.edit { preferences ->
            preferences[HA_URL] = normalizeHomeAssistantUrl(url)
            preferences[HA_TOKEN] = token.trim()
            preferences[STREAM_SOURCE] = source.trim()
            preferences[INSTANT_TWO_WAY_AUDIO] = instantTwoWayAudio
            preferences[WEBRTC_PROVIDER] = provider
            preferences[ANSWER_ACTION_SERVICE] = answerActionService.trim()
            preferences[ANSWER_ACTION_ENTITY_ID] = answerActionEntityId.trim()
            preferences[HANGUP_ACTION_SERVICE] = hangupActionService.trim()
            preferences[HANGUP_ACTION_ENTITY_ID] = hangupActionEntityId.trim()
            preferences[STREAM_BUTTON_LAYOUT] = StreamButtonLayoutJson.encode(streamButtonLayout)
        }
    }
}
