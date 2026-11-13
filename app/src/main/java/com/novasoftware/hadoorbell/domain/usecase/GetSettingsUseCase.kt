package com.novasoftware.hadoorbell.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import com.novasoftware.hadoorbell.domain.model.Settings
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<Settings> {
        return combine(
            repository.haUrlFlow,
            repository.haTokenFlow,
            repository.streamSourceFlow,
            repository.quickReplyEntityIdFlow,
            repository.lockEntityIdFlow,
            repository.instantTwoWayAudioFlow,
            repository.webrtcProviderFlow,
            repository.doorEntityIdFlow,
            repository.doorServiceFlow,
            repository.answerActionServiceFlow,
            repository.answerActionEntityIdFlow,
            repository.hangupActionServiceFlow,
            repository.hangupActionEntityIdFlow
        ) { args: Array<Any?> ->
            Settings(
                url = args[0] as? String ?: "",
                token = args[1] as? String ?: "",
                streamSource = args[2] as? String ?: "",
                quickReplyEntityId = args[3] as? String ?: "",
                lockEntityId = args[4] as? String ?: "",
                instantTwoWayAudio = args[5] as? Boolean ?: false,
                webrtcProvider = args[6] as? String ?: "frigate",
                doorEntityId = args[7] as? String ?: "",
                doorService = args[8] as? String ?: "",
                answerActionService = args[9] as? String ?: "",
                answerActionEntityId = args[10] as? String ?: "",
                hangupActionService = args[11] as? String ?: "",
                hangupActionEntityId = args[12] as? String ?: ""
            )
        }
    }
}
