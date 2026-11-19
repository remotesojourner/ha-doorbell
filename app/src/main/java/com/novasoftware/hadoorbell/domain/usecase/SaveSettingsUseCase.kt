package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.model.CallActions
import com.novasoftware.hadoorbell.domain.model.CallServiceSettings
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import javax.inject.Inject

class SaveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(settings: Settings) {
        repository.saveSettings(
            source = settings.streamSource,
            instantTwoWayAudio = settings.instantTwoWayAudio,
            provider = settings.webrtcProvider,
            callActions = CallActions(
                answer = CallServiceSettings(
                    settings.answerActionService,
                    settings.answerActionEntityId,
                    settings.answerActionData,
                    settings.answerActionNeedsResponse
                ),
                hangup = CallServiceSettings(
                    settings.hangupActionService,
                    settings.hangupActionEntityId,
                    settings.hangupActionData,
                    settings.hangupActionNeedsResponse
                )
            ),
            streamButtonLayout = settings.streamButtonLayout
        )
    }
}
