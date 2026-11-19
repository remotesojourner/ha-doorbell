package com.novasoftware.hadoorbell.domain.usecase

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.isHomeAssistantEntityId
import com.novasoftware.hadoorbell.domain.model.isHomeAssistantEventType
import com.novasoftware.hadoorbell.domain.model.isHomeAssistantServicePart
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class ExecuteStreamButtonUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(action: StreamButtonAction): Result<Unit> = when (action) {
        is StreamButtonAction.CallService -> callService(action)
        is StreamButtonAction.RunScript -> {
            val data = JsonObject()
            if (!action.variables.isEmpty) data.add("variables", action.variables)
            callEntityAction("script", "turn_on", action.entityId, data)
        }
        is StreamButtonAction.TriggerAutomation -> {
            val data = JsonObject().apply { addProperty("skip_condition", action.skipConditions) }
            callEntityAction("automation", "trigger", action.entityId, data)
        }
        is StreamButtonAction.FireEvent -> {
            val eventType = action.eventType.trim()
            if (isHomeAssistantEventType(eventType)) repository.fireEvent(eventType, action.data)
            else invalid("Enter a valid Home Assistant event type")
        }
        is StreamButtonAction.QuickReply, is StreamButtonAction.DoorLock ->
            invalid("This stream button requires its dedicated action handler")
    }

    private suspend fun callService(action: StreamButtonAction.CallService): Result<Unit> {
        val domain = action.domain.trim()
        val service = action.service.trim()
        val target = action.entityId.trim()
        if (!isHomeAssistantServicePart(domain) || !isHomeAssistantServicePart(service)) {
            return invalid("Enter a valid Home Assistant service")
        }
        if (action.doorRelease && target.isBlank() || target.isNotBlank() && !isHomeAssistantEntityId(target)) {
            return invalid("Enter a valid Home Assistant entity")
        }
        return repository.callService(domain, service, target.ifBlank { null }, action.data, action.needsResponse)
    }

    private suspend fun callEntityAction(
        domain: String, service: String, entityId: String, data: JsonObject
    ): Result<Unit> {
        val target = entityId.trim()
        if (!isHomeAssistantEntityId(target) || target.substringBefore('.') != domain) {
            return invalid("Enter a valid Home Assistant $domain entity")
        }
        return repository.callService(domain, service, target, data)
    }

    private fun invalid(message: String): Result<Unit> = Result.failure(IllegalArgumentException(message))
}
