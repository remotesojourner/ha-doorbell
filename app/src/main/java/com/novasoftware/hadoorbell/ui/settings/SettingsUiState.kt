package com.novasoftware.hadoorbell.ui.settings

data class SettingsUiState(
    val url: String = "",
    val token: String = "",
    val streamSource: String = "",
    val quickReplyEntityId: String = "",
    val lockEntityId: String = "",
    val instantTwoWayAudio: Boolean = false,
    val webrtcProvider: String = "frigate",
    val doorEntityId: String = "",
    val doorService: String = "",
    val answerActionService: String = "",
    val answerActionEntityId: String = "",
    val hangupActionService: String = "",
    val hangupActionEntityId: String = "",
    val isLoaded: Boolean = false
)
