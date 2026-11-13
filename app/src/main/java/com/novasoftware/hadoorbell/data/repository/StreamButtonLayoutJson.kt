package com.novasoftware.hadoorbell.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout

internal object StreamButtonLayoutJson {
    fun encode(layout: StreamButtonLayout): String = JsonObject().apply {
        addProperty("enabled", layout.enabled)
        add("top", encodeRow(layout.top))
        add("bottom", encodeRow(layout.bottom))
    }.toString()

    fun decode(json: String): StreamButtonLayout? = runCatching {
        val layout = JsonParser.parseString(json).asJsonObject
        StreamButtonLayout(
            top = decodeRow(layout.getAsJsonArray("top")),
            bottom = decodeRow(layout.getAsJsonArray("bottom")),
            enabled = if (layout.has("enabled")) layout.boolean("enabled") else true
        )
    }.getOrNull()

    private fun encodeRow(buttons: List<StreamButton>): JsonArray = JsonArray().apply {
        for (button in buttons) {
            add(JsonObject().apply {
                addProperty("id", button.id)
                addProperty("label", button.label)
                addProperty("requiresAuthentication", button.requiresAuthentication)
                add("action", encodeAction(button.action))
            })
        }
    }

    private fun encodeAction(action: StreamButtonAction): JsonObject = JsonObject().apply {
        when (action) {
            is StreamButtonAction.Toggle -> entityAction("toggle", action.entityId)
            is StreamButtonAction.Press -> entityAction("press", action.entityId)
            is StreamButtonAction.SelectOption -> {
                entityAction("selectOption", action.entityId)
                addProperty("option", action.option)
            }
            is StreamButtonAction.RunScript -> entityAction("runScript", action.entityId)
            is StreamButtonAction.TurnOn -> entityAction("turnOn", action.entityId)
        }
    }

    private fun JsonObject.entityAction(type: String, entityId: String) {
        addProperty("type", type)
        addProperty("entityId", entityId)
    }

    private fun decodeRow(row: JsonArray): List<StreamButton> = row.map { element ->
        val button = element.asJsonObject
        StreamButton(
            id = button.string("id"),
            label = button.string("label"),
            action = decodeAction(button.getAsJsonObject("action")),
            requiresAuthentication = button.boolean("requiresAuthentication")
        )
    }

    private fun decodeAction(action: JsonObject): StreamButtonAction = with(action) {
        when (string("type")) {
            "toggle" -> StreamButtonAction.Toggle(string("entityId"))
            "press" -> StreamButtonAction.Press(string("entityId"))
            "selectOption" -> StreamButtonAction.SelectOption(string("entityId"), string("option"))
            "runScript" -> StreamButtonAction.RunScript(string("entityId"))
            "turnOn" -> StreamButtonAction.TurnOn(string("entityId"))
            else -> error("Unsupported stream button action")
        }
    }

    private fun JsonObject.string(key: String): String =
        getAsJsonPrimitive(key).takeIf { it.isString }?.asString
            ?: error("Invalid stream button field: $key")

    private fun JsonObject.boolean(key: String): Boolean =
        getAsJsonPrimitive(key).takeIf { it.isBoolean }?.asBoolean
            ?: error("Invalid stream button field: $key")
}
