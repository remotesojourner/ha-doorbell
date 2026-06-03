package com.novasoftware.hadoorbell.domain.model

data class Settings(
    val url: String = "",
    val token: String = "",
    val streamSource: String = "",
    val quickReplyEntityId: String = "",
    val lockEntityId: String = "",
    val instantTwoWayAudio: Boolean = false,
    val webrtcProvider: String = "frigate"
)
