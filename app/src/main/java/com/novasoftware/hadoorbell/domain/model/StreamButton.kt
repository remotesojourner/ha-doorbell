package com.novasoftware.hadoorbell.domain.model

data class StreamButton(
    val id: String,
    val label: String,
    val action: StreamButtonAction,
    val requiresAuthentication: Boolean = false
)

data class StreamButtonLayout(
    val top: List<StreamButton> = emptyList(),
    val bottom: List<StreamButton> = emptyList(),
    val enabled: Boolean = true
) {
    init {
        require(top.size <= MAX_BUTTONS_PER_ROW && bottom.size <= MAX_BUTTONS_PER_ROW) {
            "Each row can contain at most $MAX_BUTTONS_PER_ROW buttons"
        }
        val ids = (top + bottom).map(StreamButton::id)
        require(ids.none(String::isBlank) && ids.size == ids.toSet().size) {
            "Stream button IDs must be nonblank and unique"
        }
    }

    companion object {
        const val MAX_BUTTONS_PER_ROW = 3
    }
}

sealed interface StreamButtonAction {
    data class Toggle(val entityId: String) : StreamButtonAction
    data class Press(val entityId: String) : StreamButtonAction
    data class SelectOption(val entityId: String, val option: String) : StreamButtonAction
    data class RunScript(val entityId: String) : StreamButtonAction
    data class TurnOn(val entityId: String) : StreamButtonAction

    companion object {
        val TOGGLE_DOMAINS = setOf(
            "automation", "climate", "cover", "fan", "humidifier", "input_boolean",
            "light", "media_player", "remote", "script", "siren", "switch", "valve"
        )
        val PRESS_DOMAINS = setOf("button", "input_button")
        val SELECT_DOMAINS = setOf("select", "input_select")
        val TURN_ON_DOMAINS = setOf("switch", "input_boolean")
        val SCRIPT_DOMAINS = setOf("script")
    }
}
