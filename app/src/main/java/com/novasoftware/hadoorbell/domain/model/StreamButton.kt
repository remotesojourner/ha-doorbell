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

enum class StreamControl { SOUND, CALL }

sealed interface StreamBottomItem {
    data class Control(val control: StreamControl) : StreamBottomItem
    data class Custom(val button: StreamButton) : StreamBottomItem
}

data class StreamButtonLayout(
    val top: List<StreamButton> = emptyList(),
    val bottom: List<StreamButton> = emptyList(),
    val soundPosition: Int = DEFAULT_SOUND_POSITION,
    val callPosition: Int = DEFAULT_CALL_POSITION
) {
    val buttons: List<StreamButton> get() = top + bottom
    val hasQuickReply: Boolean get() = buttons.any { it.visible && it.action is StreamButtonAction.QuickReply }

    /** The bottom row in display order, with Sound and Call placed among the custom buttons. */
    val bottomItems: List<StreamBottomItem>
        get() {
            val items = bottom.mapTo(mutableListOf<StreamBottomItem>(), StreamBottomItem::Custom)
            StreamControl.entries.sortedBy(::position).forEach { control ->
                items.add(position(control).coerceAtMost(items.size), StreamBottomItem.Control(control))
            }
            return items
        }

    fun position(control: StreamControl): Int = when (control) {
        StreamControl.SOUND -> soundPosition
        StreamControl.CALL -> callPosition
    }

    fun withBottomItems(items: List<StreamBottomItem>): StreamButtonLayout {
        fun indexOf(control: StreamControl) = items.indexOf(StreamBottomItem.Control(control)).also {
            require(it >= 0) { "The bottom row must contain the $control control" }
        }
        return copy(
            bottom = items.filterIsInstance<StreamBottomItem.Custom>().map(StreamBottomItem.Custom::button),
            soundPosition = indexOf(StreamControl.SOUND),
            callPosition = indexOf(StreamControl.CALL)
        )
    }

    init {
        require(top.size <= MAX_BUTTONS_PER_ROW && bottom.size <= MAX_BUTTONS_PER_ROW) {
            "Each row can contain at most $MAX_BUTTONS_PER_ROW buttons"
        }
        val ids = buttons.map(StreamButton::id)
        require(ids.none(String::isBlank) && ids.size == ids.toSet().size) {
            "Stream button IDs must be nonblank and unique"
        }
        require(soundPosition >= 0 && callPosition >= 0 && soundPosition != callPosition) {
            "Sound and Call need distinct, nonnegative positions"
        }
    }

    companion object {
        const val MAX_BUTTONS_PER_ROW = 3
        const val DEFAULT_SOUND_POSITION = 0
        const val DEFAULT_CALL_POSITION = 1
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
