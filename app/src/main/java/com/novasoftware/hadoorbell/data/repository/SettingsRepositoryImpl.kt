package com.novasoftware.hadoorbell.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.novasoftware.hadoorbell.domain.model.CallActions
import com.novasoftware.hadoorbell.domain.model.CallServiceSettings
import com.novasoftware.hadoorbell.domain.model.HomeAssistantConnection
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
        val ANSWER_ACTION_DATA = stringPreferencesKey("answer_action_data")
        val ANSWER_ACTION_NEEDS_RESPONSE = booleanPreferencesKey("answer_action_needs_response")
        val HANGUP_ACTION_SERVICE = stringPreferencesKey("hangup_action_service")
        val HANGUP_ACTION_ENTITY_ID = stringPreferencesKey("hangup_action_entity_id")
        val HANGUP_ACTION_DATA = stringPreferencesKey("hangup_action_data")
        val HANGUP_ACTION_NEEDS_RESPONSE = booleanPreferencesKey("hangup_action_needs_response")
    }

    override val homeAssistantConnectionFlow: Flow<HomeAssistantConnection> = context.dataStore.data.map {
        HomeAssistantConnection(url = it[HA_URL].orEmpty().trim(), token = it[HA_TOKEN].orEmpty().trim())
    }
    override val streamSourceFlow: Flow<String?> = context.dataStore.data.map { it[STREAM_SOURCE]?.trim() }
    override val instantTwoWayAudioFlow: Flow<Boolean> = context.dataStore.data.map { it[INSTANT_TWO_WAY_AUDIO] ?: false }
    override val webrtcProviderFlow: Flow<String> = context.dataStore.data.map { it[WEBRTC_PROVIDER] ?: "frigate" }
    override val callActionsFlow: Flow<CallActions> = context.dataStore.data.map {
        CallActions(
            answer = CallServiceSettings(
                service = it[ANSWER_ACTION_SERVICE].orEmpty().trim(),
                entityId = it[ANSWER_ACTION_ENTITY_ID].orEmpty().trim(),
                data = decodeActionData(it[ANSWER_ACTION_DATA]),
                needsResponse = it[ANSWER_ACTION_NEEDS_RESPONSE] ?: false
            ),
            hangup = CallServiceSettings(
                service = it[HANGUP_ACTION_SERVICE].orEmpty().trim(),
                entityId = it[HANGUP_ACTION_ENTITY_ID].orEmpty().trim(),
                data = decodeActionData(it[HANGUP_ACTION_DATA]),
                needsResponse = it[HANGUP_ACTION_NEEDS_RESPONSE] ?: false
            )
        )
    }
    override val streamButtonLayoutFlow: Flow<StreamButtonLayout> = context.dataStore.data.map {
        it[STREAM_BUTTON_LAYOUT]?.let(StreamButtonLayoutJson::decode) ?: StreamButtonLayout()
    }

    override suspend fun saveHomeAssistantConnection(url: String, token: String) {
        context.dataStore.edit {
            it[HA_URL] = normalizeHomeAssistantUrl(url)
            it[HA_TOKEN] = token.trim()
        }
    }

    override suspend fun saveSettings(
        source: String,
        instantTwoWayAudio: Boolean,
        provider: String,
        callActions: CallActions,
        streamButtonLayout: StreamButtonLayout
    ) {
        context.dataStore.edit { preferences ->
            preferences[STREAM_SOURCE] = source.trim()
            preferences[INSTANT_TWO_WAY_AUDIO] = instantTwoWayAudio
            preferences[WEBRTC_PROVIDER] = provider
            preferences[ANSWER_ACTION_SERVICE] = callActions.answer.service.trim()
            preferences[ANSWER_ACTION_ENTITY_ID] = callActions.answer.entityId.trim()
            preferences[ANSWER_ACTION_DATA] = callActions.answer.data.toString()
            preferences[ANSWER_ACTION_NEEDS_RESPONSE] = callActions.answer.needsResponse
            preferences[HANGUP_ACTION_SERVICE] = callActions.hangup.service.trim()
            preferences[HANGUP_ACTION_ENTITY_ID] = callActions.hangup.entityId.trim()
            preferences[HANGUP_ACTION_DATA] = callActions.hangup.data.toString()
            preferences[HANGUP_ACTION_NEEDS_RESPONSE] = callActions.hangup.needsResponse
            preferences[STREAM_BUTTON_LAYOUT] = StreamButtonLayoutJson.encode(streamButtonLayout)
        }
    }

    private fun decodeActionData(value: String?): JsonObject {
        if (value == null) return JsonObject()
        return try {
            JsonParser.parseString(value) as? JsonObject ?: JsonObject()
        } catch (_: JsonParseException) {
            JsonObject()
        }
    }
}
