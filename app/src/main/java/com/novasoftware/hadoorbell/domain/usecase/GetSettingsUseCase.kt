package com.novasoftware.hadoorbell.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<Settings> {
        return combine(
            repository.haUrlFlow,
            repository.haTokenFlow,
            repository.streamSourceFlow,
            repository.instantTwoWayAudioFlow,
            repository.webrtcProviderFlow,
            repository.answerActionServiceFlow,
            repository.answerActionEntityIdFlow,
            repository.hangupActionServiceFlow,
            repository.hangupActionEntityIdFlow,
            repository.streamButtonLayoutFlow
        ) { args: Array<Any?> ->
            Settings(
                url = args[0] as? String ?: "",
                token = args[1] as? String ?: "",
                streamSource = args[2] as? String ?: "",
                instantTwoWayAudio = args[3] as? Boolean ?: false,
                webrtcProvider = args[4] as? String ?: "frigate",
                answerActionService = args[5] as? String ?: "",
                answerActionEntityId = args[6] as? String ?: "",
                hangupActionService = args[7] as? String ?: "",
                hangupActionEntityId = args[8] as? String ?: "",
                streamButtonLayout = args[9] as StreamButtonLayout
            )
        }
    }
}
