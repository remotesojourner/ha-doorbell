package com.novasoftware.hadoorbell.domain.model

import com.google.gson.JsonObject

data class StreamButton(
    val id: String,
    val action: StreamButtonAction,
    val requiresAuthentication: Boolean = false,
    val visible: Boolean = true,
    val icon: String? = null,
    val entityIcon: String? = null,
    val entityName: String? = null,
    val stateEntityId: String? = null,
    val stateColors: Map<String, String> = emptyMap()
) {
    val iconEntityId: String get() = stateEntityId?.trim()?.takeIf(String::isNotEmpty) ?: action.entityId

    fun colorForState(state: String?): String? {
        if (state == null || stateEntityId.isNullOrBlank()) return null
        return stateColors[state]?.let(::normalizeStreamButtonColor)
    }

    val displayName: String get() = entityName?.takeIf { it.isNotBlank() } ?: when (val currentAction = action) {
        is StreamButtonAction.CallService -> currentAction.entityId.ifBlank {
            "${currentAction.domain}.${currentAction.service}"
        }
        is StreamButtonAction.FireEvent -> currentAction.eventType
        else -> currentAction.entityId
    }
}

private val streamButtonColorHex = Regex("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")

fun normalizeStreamButtonColor(value: String): String? =
    value.trim().removePrefix("#").takeIf(streamButtonColorHex::matches)?.let { "#${it.uppercase()}" }

data class StreamButtonLayout(
    val top: List<StreamButton> = emptyList(),
    val bottom: List<StreamButton> = emptyList()
) {
    val buttons: List<StreamButton> get() = top + bottom
    val hasQuickReply: Boolean get() = buttons.any { it.visible && it.action is StreamButtonAction.QuickReply }

    init {
        require(top.size <= MAX_BUTTONS_PER_ROW && bottom.size <= MAX_BUTTONS_PER_ROW) {
            "Each row can contain at most $MAX_BUTTONS_PER_ROW buttons"
        }
        val ids = buttons.map(StreamButton::id)
        require(ids.none(String::isBlank) && ids.size == ids.toSet().size) {
            "Stream button IDs must be nonblank and unique"
        }
    }

    companion object {
        const val MAX_BUTTONS_PER_ROW = 3
    }
}

sealed interface StreamButtonAction {
    val entityId: String

    data class CallService(
        val domain: String,
        val service: String,
        override val entityId: String = "",
        val data: JsonObject = JsonObject(),
        val doorRelease: Boolean = false,
        val needsResponse: Boolean = false
    ) : StreamButtonAction

    data class RunScript(
        override val entityId: String,
        val variables: JsonObject = JsonObject()
    ) : StreamButtonAction

    data class TriggerAutomation(
        override val entityId: String,
        val skipConditions: Boolean = false
    ) : StreamButtonAction

    data class FireEvent(val eventType: String, val data: JsonObject = JsonObject()) : StreamButtonAction {
        override val entityId: String get() = ""
    }

    data class QuickReply(override val entityId: String) : StreamButtonAction
    data class DoorLock(override val entityId: String) : StreamButtonAction

    companion object {
        val SELECT_DOMAINS = setOf("select", "input_select")
        val SCRIPT_DOMAINS = setOf("script")
        val LOCK_DOMAINS = setOf("lock")
    }
}
