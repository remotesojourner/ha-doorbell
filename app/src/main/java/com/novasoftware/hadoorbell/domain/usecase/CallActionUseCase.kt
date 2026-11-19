package com.novasoftware.hadoorbell.domain.usecase

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class CallActionUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(
        service: String,
        entityId: String = "",
        data: JsonObject = JsonObject(),
        needsResponse: Boolean = false
    ): Result<Unit> {
        val trimmedService = service.trim()
        val domain = trimmedService.substringBefore('.', "")
        val name = trimmedService.substringAfter('.', "")

        if (domain.isBlank() || name.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid action '$trimmedService', expected domain.service"))
        }

        val target = entityId.trim().ifBlank { null }
        return if (data.isEmpty && !needsResponse) {
            repository.callService(domain, name, target)
        } else {
            repository.callService(domain, name, target, data, needsResponse)
        }
    }
}
