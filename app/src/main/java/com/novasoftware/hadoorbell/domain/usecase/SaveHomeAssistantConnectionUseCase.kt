package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import javax.inject.Inject

class SaveHomeAssistantConnectionUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(url: String, token: String) {
        repository.saveHomeAssistantConnection(url, token)
    }
}
