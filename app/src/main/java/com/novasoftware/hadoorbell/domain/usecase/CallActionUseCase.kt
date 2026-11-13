package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class CallActionUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(service: String, entityId: String = ""): Result<Unit> {
        val trimmedService = service.trim()
        val domain = trimmedService.substringBefore('.', "")
        val name = trimmedService.substringAfter('.', "")

        if (domain.isBlank() || name.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid action '$trimmedService', expected domain.service"))
        }

        return repository.callService(domain, name, entityId.trim().ifBlank { null })
    }
}
