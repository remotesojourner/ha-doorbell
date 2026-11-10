package com.novasoftware.hadoorbell.domain.usecase

import javax.inject.Inject

class OpenDoorUseCase @Inject constructor(
    private val callActionUseCase: CallActionUseCase
) {
    suspend operator fun invoke(entityId: String, service: String = ""): Result<Unit> {
        val entity = entityId.trim()
        if (entity.isBlank()) {
            return Result.failure(IllegalStateException("No door entity configured"))
        }

        val resolvedService = service.trim().ifBlank { defaultServiceFor(entity) }
        if (resolvedService.isBlank()) {
            return Result.failure(IllegalStateException("No door service configured for '$entity'"))
        }

        return callActionUseCase(resolvedService, entity)
    }

    companion object {
        fun defaultServiceFor(entityId: String): String = when (entityId.substringBefore('.', "")) {
            "lock" -> "lock.unlock"
            "switch" -> "switch.turn_on"
            "input_boolean" -> "input_boolean.turn_on"
            "button" -> "button.press"
            "cover" -> "cover.open_cover"
            "script" -> "script.turn_on"
            "scene" -> "scene.turn_on"
            else -> ""
        }
    }
}
