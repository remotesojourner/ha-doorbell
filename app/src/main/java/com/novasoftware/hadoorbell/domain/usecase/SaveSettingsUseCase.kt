package com.novasoftware.hadoorbell.domain.usecase

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
            answerActionService = settings.answerActionService,
            answerActionEntityId = settings.answerActionEntityId,
            hangupActionService = settings.hangupActionService,
            hangupActionEntityId = settings.hangupActionEntityId,
            streamButtonLayout = settings.streamButtonLayout
        )
    }
}
