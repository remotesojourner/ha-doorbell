package com.novasoftware.hadoorbell.domain.repository

import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val haUrlFlow: Flow<String?>
    val haTokenFlow: Flow<String?>
    val streamSourceFlow: Flow<String?>
    val quickReplyEntityIdFlow: Flow<String?>
    val lockEntityIdFlow: Flow<String?>
    val instantTwoWayAudioFlow: Flow<Boolean>
    val webrtcProviderFlow: Flow<String>
    val doorEntityIdFlow: Flow<String?>
    val doorServiceFlow: Flow<String?>
    val answerActionServiceFlow: Flow<String?>
    val answerActionEntityIdFlow: Flow<String?>
    val hangupActionServiceFlow: Flow<String?>
    val hangupActionEntityIdFlow: Flow<String?>
    val streamButtonLayoutFlow: Flow<StreamButtonLayout?>

    suspend fun saveSettings(
        url: String, 
        token: String, 
        source: String, 
        quickReplyEntityId: String, 
        lockEntityId: String, 
        instantTwoWayAudio: Boolean = false, 
        provider: String = "frigate",
        doorEntityId: String = "",
        doorService: String = "",
        answerActionService: String = "",
        answerActionEntityId: String = "",
        hangupActionService: String = "",
        hangupActionEntityId: String = "",
        streamButtonLayout: StreamButtonLayout? = null
    )
}
