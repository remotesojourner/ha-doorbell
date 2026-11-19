package com.novasoftware.hadoorbell.ui.settings

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout

enum class HomeAssistantLoadError {
    MissingConnection,
    ChangedServer,
    InvalidConnection,
    AuthenticationFailed,
    RequestFailed,
    ConnectionSaveFailed
}

sealed interface HomeAssistantConnectionStatus {
    data object Unchecked : HomeAssistantConnectionStatus
    data object Checking : HomeAssistantConnectionStatus
    data object Verified : HomeAssistantConnectionStatus
    data class Failed(val error: HomeAssistantLoadError) : HomeAssistantConnectionStatus
}

data class SettingsUiState(
    val url: String = "",
    val token: String = "",
    val streamSource: String = "",
    val instantTwoWayAudio: Boolean = false,
    val webrtcProvider: String = "frigate",
    val answerActionService: String = "",
    val answerActionEntityId: String = "",
    val answerActionData: JsonObject = JsonObject(),
    val answerActionNeedsResponse: Boolean = false,
    val hangupActionService: String = "",
    val hangupActionEntityId: String = "",
    val hangupActionData: JsonObject = JsonObject(),
    val hangupActionNeedsResponse: Boolean = false,
    val isLoaded: Boolean = false,
    val connectionStatus: HomeAssistantConnectionStatus = HomeAssistantConnectionStatus.Unchecked,
    val streamButtonLayout: StreamButtonLayout = StreamButtonLayout(),
    val entities: List<HomeAssistantEntity> = emptyList(),
    val isLoadingEntities: Boolean = false,
    val entityLoadError: HomeAssistantLoadError? = null,
    val services: List<HomeAssistantService> = emptyList(),
    val isLoadingServices: Boolean = false,
    val serviceLoadError: HomeAssistantLoadError? = null,
    val streams: List<String> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val streamLoadError: HomeAssistantLoadError? = null
)
