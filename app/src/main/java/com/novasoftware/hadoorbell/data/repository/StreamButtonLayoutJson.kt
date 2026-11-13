package com.novasoftware.hadoorbell.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout

internal object StreamButtonLayoutJson {
    fun encode(layout: StreamButtonLayout): String = JsonObject().apply {
        addProperty("buttonVisibility", true)
        add("top", encodeRow(layout.top))
        add("bottom", encodeRow(layout.bottom))
    }.toString()

    fun decode(json: String): StreamButtonLayout? = runCatching {
        val layout = JsonParser.parseString(json).asJsonObject
        StreamButtonLayout(
            top = decodeRow(layout.getAsJsonArray("top")),
            bottom = decodeRow(layout.getAsJsonArray("bottom"))
        )
    }.getOrNull()

    fun previousEnabledFlag(json: String): Boolean? = runCatching {
        val layout = JsonParser.parseString(json).asJsonObject
        if (layout.has("enabled")) layout.boolean("enabled") else null
    }.getOrNull()

    fun hasVisibilityMarker(json: String): Boolean = runCatching {
        JsonParser.parseString(json).asJsonObject.has("buttonVisibility")
    }.getOrDefault(false)

    private fun encodeRow(buttons: List<StreamButton>): JsonArray = JsonArray().apply {
        for (button in buttons) {
            add(JsonObject().apply {
                addProperty("id", button.id)
                addProperty("label", button.label)
                addProperty("requiresAuthentication", button.requiresAuthentication)
                addProperty("visible", button.visible)
                button.icon?.let { addProperty("icon", it) }
                button.entityIcon?.let { addProperty("entityIcon", it) }
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
            is StreamButtonAction.QuickReply -> entityAction("quickReply", action.entityId)
            is StreamButtonAction.DoorLock -> entityAction("doorLock", action.entityId)
            is StreamButtonAction.OpenDoor -> {
                entityAction("openDoor", action.entityId)
                addProperty("service", action.service)
            }
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
            requiresAuthentication = button.boolean("requiresAuthentication"),
            visible = if (button.has("visible")) button.boolean("visible") else true,
            icon = button.optionalString("icon"),
            entityIcon = button.optionalString("entityIcon")
        )
    }

    private fun decodeAction(action: JsonObject): StreamButtonAction = with(action) {
        when (string("type")) {
            "toggle" -> StreamButtonAction.Toggle(string("entityId"))
            "press" -> StreamButtonAction.Press(string("entityId"))
            "selectOption" -> StreamButtonAction.SelectOption(string("entityId"), string("option"))
            "runScript" -> StreamButtonAction.RunScript(string("entityId"))
            "turnOn" -> StreamButtonAction.TurnOn(string("entityId"))
            "quickReply" -> StreamButtonAction.QuickReply(string("entityId"))
            "doorLock" -> StreamButtonAction.DoorLock(string("entityId"))
            "openDoor" -> StreamButtonAction.OpenDoor(string("entityId"), string("service"))
            else -> error("Unsupported stream button action")
        }
    }

    private fun JsonObject.string(key: String): String =
        getAsJsonPrimitive(key).takeIf { it.isString }?.asString
            ?: error("Invalid stream button field: $key")

    private fun JsonObject.optionalString(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.boolean(key: String): Boolean =
        getAsJsonPrimitive(key).takeIf { it.isBoolean }?.asBoolean
            ?: error("Invalid stream button field: $key")
}
