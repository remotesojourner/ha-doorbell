package com.novasoftware.hadoorbell.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val haUrlFlow: Flow<String?>
    val haTokenFlow: Flow<String?>
    val streamSourceFlow: Flow<String?>
    val quickReplyEntityIdFlow: Flow<String?>
    val lockEntityIdFlow: Flow<String?>
    val instantTwoWayAudioFlow: Flow<Boolean>
    val webrtcProviderFlow: Flow<String>

    suspend fun saveSettings(
        url: String, 
        token: String, 
        source: String, 
        quickReplyEntityId: String, 
        lockEntityId: String, 
        instantTwoWayAudio: Boolean = false, 
        provider: String = "frigate"
    )
}
