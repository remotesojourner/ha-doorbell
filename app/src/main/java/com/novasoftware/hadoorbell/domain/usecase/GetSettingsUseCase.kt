package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.model.HomeAssistantConnection
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<Settings> {
        return combine(
            repository.homeAssistantConnectionFlow,
            repository.streamSourceFlow,
            repository.instantTwoWayAudioFlow,
            repository.webrtcProviderFlow,
            repository.answerActionServiceFlow,
            repository.answerActionEntityIdFlow,
            repository.hangupActionServiceFlow,
            repository.hangupActionEntityIdFlow,
            repository.streamButtonLayoutFlow
        ) { args: Array<Any?> ->
            val connection = args[0] as HomeAssistantConnection
            Settings(
                url = connection.url,
                token = connection.token,
                streamSource = args[1] as? String ?: "",
                instantTwoWayAudio = args[2] as? Boolean ?: false,
                webrtcProvider = args[3] as? String ?: "frigate",
                answerActionService = args[4] as? String ?: "",
                answerActionEntityId = args[5] as? String ?: "",
                hangupActionService = args[6] as? String ?: "",
                hangupActionEntityId = args[7] as? String ?: "",
                streamButtonLayout = args[8] as StreamButtonLayout
            )
        }
    }
}
