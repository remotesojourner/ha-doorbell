package com.novasoftware.hadoorbell.ui.settings

import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout

enum class HomeAssistantLoadError {
    MissingConnection,
    ChangedServer,
    InvalidConnection,
    AuthenticationFailed,
    RequestFailed
}

data class SettingsUiState(
    val url: String = "",
    val token: String = "",
    val streamSource: String = "",
    val instantTwoWayAudio: Boolean = false,
    val webrtcProvider: String = "frigate",
    val answerActionService: String = "",
    val answerActionEntityId: String = "",
    val hangupActionService: String = "",
    val hangupActionEntityId: String = "",
    val isLoaded: Boolean = false,
    val streamButtonLayout: StreamButtonLayout = StreamButtonLayout(),
    val entities: List<HomeAssistantEntity> = emptyList(),
    val isLoadingEntities: Boolean = false,
    val entityLoadError: HomeAssistantLoadError? = null,
    val services: List<HomeAssistantService> = emptyList(),
    val isLoadingServices: Boolean = false,
    val serviceLoadError: HomeAssistantLoadError? = null
)
