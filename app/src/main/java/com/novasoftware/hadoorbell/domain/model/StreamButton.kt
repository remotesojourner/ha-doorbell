package com.novasoftware.hadoorbell.domain.model

data class StreamButton(
    val id: String,
    val action: StreamButtonAction,
    val requiresAuthentication: Boolean = false,
    val visible: Boolean = true,
    val icon: String? = null,
    val entityIcon: String? = null,
    val entityName: String? = null
) {
    val displayName: String get() = entityName?.takeIf { it.isNotBlank() } ?: action.entityId
}

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

    data class Toggle(override val entityId: String) : StreamButtonAction
    data class Press(override val entityId: String) : StreamButtonAction
    data class SelectOption(override val entityId: String, val option: String) : StreamButtonAction
    data class RunScript(override val entityId: String) : StreamButtonAction
    data class TurnOn(override val entityId: String) : StreamButtonAction
    data class QuickReply(override val entityId: String) : StreamButtonAction
    data class DoorLock(override val entityId: String) : StreamButtonAction
    data class OpenDoor(override val entityId: String, val service: String = "") : StreamButtonAction

    companion object {
        val TOGGLE_DOMAINS = setOf(
            "automation", "climate", "cover", "fan", "humidifier", "input_boolean",
            "light", "media_player", "remote", "script", "siren", "switch", "valve"
        )
        val PRESS_DOMAINS = setOf("button", "input_button")
        val SELECT_DOMAINS = setOf("select", "input_select")
        val TURN_ON_DOMAINS = setOf("switch", "input_boolean")
        val SCRIPT_DOMAINS = setOf("script")
        val LOCK_DOMAINS = setOf("lock")
    }
}
