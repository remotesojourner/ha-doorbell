package com.novasoftware.hadoorbell.domain.model

import com.google.gson.JsonObject

data class HomeAssistantConnection(val url: String, val token: String)

data class CallServiceSettings(
    val service: String = "",
    val entityId: String = "",
    val data: JsonObject = JsonObject(),
    val needsResponse: Boolean = false
)

data class CallActions(
    val answer: CallServiceSettings = CallServiceSettings(),
    val hangup: CallServiceSettings = CallServiceSettings()
)

data class Settings(
    val url: String = "",
    val token: String = "",
    val streamSource: String = "",
    val instantTwoWayAudio: Boolean = false,
    val webrtcProvider: String = "frigate",
    val answerActionService: String = "",
    val answerActionEntityId: String = "",
    val hangupActionService: String = "",
    val hangupActionEntityId: String = "",
    val streamButtonLayout: StreamButtonLayout = StreamButtonLayout(),
    val answerActionData: JsonObject = JsonObject(),
    val hangupActionData: JsonObject = JsonObject(),
    val answerActionNeedsResponse: Boolean = false,
    val hangupActionNeedsResponse: Boolean = false
)
