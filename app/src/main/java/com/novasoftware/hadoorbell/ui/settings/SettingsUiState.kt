package com.novasoftware.hadoorbell.ui.settings

data class SettingsUiState(
    val url: String = "",
    val token: String = "",
    val streamSource: String = "",
    val quickReplyEntityId: String = "",
    val lockEntityId: String = "",
    val instantTwoWayAudio: Boolean = false,
    val webrtcProvider: String = "frigate",
    val isLoaded: Boolean = false
)
