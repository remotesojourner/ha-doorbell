package com.novasoftware.hadoorbell.domain.repository

import com.novasoftware.hadoorbell.domain.model.CallActions
import com.novasoftware.hadoorbell.domain.model.HomeAssistantConnection
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val homeAssistantConnectionFlow: Flow<HomeAssistantConnection>
    val streamSourceFlow: Flow<String?>
    val instantTwoWayAudioFlow: Flow<Boolean>
    val webrtcProviderFlow: Flow<String>
    val callActionsFlow: Flow<CallActions>
    val streamButtonLayoutFlow: Flow<StreamButtonLayout>

    suspend fun saveHomeAssistantConnection(url: String, token: String)

    suspend fun saveSettings(
        source: String,
        instantTwoWayAudio: Boolean = false,
        provider: String = "frigate",
        callActions: CallActions = CallActions(),
        streamButtonLayout: StreamButtonLayout = StreamButtonLayout()
    )
}
