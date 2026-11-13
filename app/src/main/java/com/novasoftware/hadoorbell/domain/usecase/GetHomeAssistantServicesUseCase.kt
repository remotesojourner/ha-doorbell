package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class GetHomeAssistantServicesUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(url: String, token: String): Result<List<HomeAssistantService>> =
        repository.getServices(url, token)
}
