package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.model.CallActions
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
            repository.callActionsFlow,
            repository.streamButtonLayoutFlow
        ) { args: Array<Any?> ->
            val connection = args[0] as HomeAssistantConnection
            val callActions = args[4] as CallActions
            Settings(
                url = connection.url,
                token = connection.token,
                streamSource = args[1] as? String ?: "",
                instantTwoWayAudio = args[2] as? Boolean ?: false,
                webrtcProvider = args[3] as? String ?: "frigate",
                answerActionService = callActions.answer.service,
                answerActionEntityId = callActions.answer.entityId,
                hangupActionService = callActions.hangup.service,
                hangupActionEntityId = callActions.hangup.entityId,
                streamButtonLayout = args[5] as StreamButtonLayout,
                answerActionData = callActions.answer.data,
                hangupActionData = callActions.hangup.data,
                answerActionNeedsResponse = callActions.answer.needsResponse,
                hangupActionNeedsResponse = callActions.hangup.needsResponse
            )
        }
    }
}
