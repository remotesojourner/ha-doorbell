package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class ExecuteStreamButtonUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(action: StreamButtonAction): Result<Unit> = when (action) {
        is StreamButtonAction.Toggle -> callEntityService(
            action.entityId, StreamButtonAction.TOGGLE_DOMAINS, "toggle"
        )
        is StreamButtonAction.Press -> callEntityService(
            action.entityId, StreamButtonAction.PRESS_DOMAINS, "press"
        )
        is StreamButtonAction.SelectOption -> {
            val entityId = action.entityId.trim()
            if (entityDomain(entityId) !in StreamButtonAction.SELECT_DOMAINS || action.option.isBlank()) {
                Result.failure(IllegalArgumentException("Select a valid Home Assistant entity and option"))
            } else {
                repository.setSelectOption(entityId, action.option)
            }
        }
        is StreamButtonAction.RunScript -> callEntityService(
            action.entityId, StreamButtonAction.SCRIPT_DOMAINS, "turn_on"
        )
        is StreamButtonAction.TurnOn -> callEntityService(
            action.entityId, StreamButtonAction.TURN_ON_DOMAINS, "turn_on"
        )
    }

    private suspend fun callEntityService(
        entityId: String,
        domains: Set<String>,
        service: String
    ): Result<Unit> {
        val target = entityId.trim()
        val domain = entityDomain(target)
        if (domain !in domains) {
            return Result.failure(IllegalArgumentException("Unsupported Home Assistant entity: '$target'"))
        }
        return repository.callService(domain, service, target)
    }

    private fun entityDomain(entityId: String): String =
        entityId.takeIf(ENTITY_ID::matches)?.substringBefore('.').orEmpty()

    private companion object {
        val ENTITY_ID = Regex("[a-z0-9_]+\\.[a-z0-9_]+")
    }
}
