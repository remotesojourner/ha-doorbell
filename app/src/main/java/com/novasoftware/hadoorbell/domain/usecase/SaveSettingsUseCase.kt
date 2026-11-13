package com.novasoftware.hadoorbell.domain.usecase

import javax.inject.Inject
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import com.novasoftware.hadoorbell.domain.model.Settings

class SaveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(settings: Settings) {
        repository.saveSettings(
            url = settings.url,
            token = settings.token,
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
