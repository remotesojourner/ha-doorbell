package com.novasoftware.hadoorbell.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.model.normalizeStreamButtonColor
import com.novasoftware.hadoorbell.domain.usecase.OpenDoorUseCase

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

    fun hasLegacyLabels(json: String): Boolean = runCatching {
        val layout = JsonParser.parseString(json).asJsonObject
        listOf("top", "bottom").any { row ->
            layout.getAsJsonArray(row).any { it.asJsonObject.has("label") }
        }
    }.getOrDefault(false)

    fun hasLegacyActions(json: String): Boolean = runCatching {
        val layout = JsonParser.parseString(json).asJsonObject
        listOf("top", "bottom").any { row ->
            layout.getAsJsonArray(row).any {
                it.asJsonObject.getAsJsonObject("action").string("type") in legacyActionTypes
            }
        }
    }.getOrDefault(false)

    fun legacyBottomLabels(json: String): List<String?> = runCatching {
        JsonParser.parseString(json).asJsonObject.getAsJsonArray("bottom")
            .map { it.asJsonObject.optionalString("label") }
    }.getOrDefault(emptyList())

    private fun encodeRow(buttons: List<StreamButton>): JsonArray = JsonArray().apply {
        for (button in buttons) {
            add(JsonObject().apply {
                addProperty("id", button.id)
                addProperty("requiresAuthentication", button.requiresAuthentication)
                addProperty("visible", button.visible)
                button.icon?.let { addProperty("icon", it) }
                button.entityIcon?.let { addProperty("entityIcon", it) }
                button.entityName?.let { addProperty("entityName", it) }
                button.stateEntityId?.let { addProperty("stateEntityId", it) }
                if (!button.stateEntityId.isNullOrBlank()) {
                    val colors = JsonObject().apply {
                        button.stateColors.forEach { (state, value) ->
                            if (state.isNotBlank()) normalizeStreamButtonColor(value)?.let { addProperty(state, it) }
                        }
                    }
                    if (!colors.isEmpty) add("stateColors", colors)
                }
                add("action", encodeAction(button.action))
            })
        }
    }

    private fun encodeAction(action: StreamButtonAction): JsonObject = JsonObject().apply {
        when (action) {
            is StreamButtonAction.CallService -> {
                entityAction("callService", action.entityId)
                addProperty("domain", action.domain)
                addProperty("service", action.service)
                if (!action.data.isEmpty) add("data", action.data)
                if (action.doorRelease) addProperty("doorRelease", true)
                if (action.needsResponse) addProperty("needsResponse", true)
            }
            is StreamButtonAction.RunScript -> {
                entityAction("runScript", action.entityId)
                if (!action.variables.isEmpty) add("variables", action.variables)
            }
            is StreamButtonAction.TriggerAutomation -> {
                entityAction("triggerAutomation", action.entityId)
                addProperty("skipConditions", action.skipConditions)
            }
            is StreamButtonAction.FireEvent -> {
                addProperty("type", "fireEvent")
                addProperty("eventType", action.eventType)
                if (!action.data.isEmpty) add("data", action.data)
            }
            is StreamButtonAction.QuickReply -> entityAction("quickReply", action.entityId)
            is StreamButtonAction.DoorLock -> entityAction("doorLock", action.entityId)
        }
    }

    private fun JsonObject.entityAction(type: String, entityId: String) {
        addProperty("type", type)
        addProperty("entityId", entityId)
    }

    private fun decodeRow(row: JsonArray): List<StreamButton> = row.map { element ->
        val button = element.asJsonObject
        val stateEntityId = button.optionalString("stateEntityId")
        StreamButton(
            id = button.string("id"),
            action = decodeAction(button.getAsJsonObject("action")),
            requiresAuthentication = button.boolean("requiresAuthentication"),
            visible = if (button.has("visible")) button.boolean("visible") else true,
            icon = button.optionalString("icon"),
            entityIcon = button.optionalString("entityIcon"),
            entityName = button.optionalString("entityName"),
            stateEntityId = stateEntityId,
            stateColors = if (stateEntityId == null) emptyMap() else button.decodeStateColors()
        )
    }

    private fun JsonObject.decodeStateColors(): Map<String, String> = buildMap {
        val colors = this@decodeStateColors.get("stateColors") as? JsonObject ?: return@buildMap
        for ((state, value) in colors.entrySet()) {
            if (state.isNotBlank() && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                normalizeStreamButtonColor(value.asString)?.let { put(state, it) }
            }
        }
    }

    private fun decodeAction(action: JsonObject): StreamButtonAction = with(action) {
        when (string("type")) {
            "callService" -> StreamButtonAction.CallService(
                domain = string("domain"),
                service = string("service"),
                entityId = optionalString("entityId").orEmpty(),
                data = objectOrEmpty("data"),
                doorRelease = has("doorRelease") && boolean("doorRelease"),
                needsResponse = has("needsResponse") && boolean("needsResponse")
            )
            "runScript" -> StreamButtonAction.RunScript(string("entityId"), objectOrEmpty("variables"))
            "triggerAutomation" -> StreamButtonAction.TriggerAutomation(
                string("entityId"), has("skipConditions") && boolean("skipConditions")
            )
            "fireEvent" -> StreamButtonAction.FireEvent(string("eventType"), objectOrEmpty("data"))
            "quickReply" -> StreamButtonAction.QuickReply(string("entityId"))
            "doorLock" -> StreamButtonAction.DoorLock(string("entityId"))
            "toggle" -> legacyService(string("entityId"), "toggle")
            "press" -> legacyService(string("entityId"), "press")
            "turnOn" -> legacyService(string("entityId"), "turn_on")
            "selectOption" -> {
                val option = string("option")
                legacyService(
                    string("entityId"), "select_option", JsonObject().apply { addProperty("option", option) }
                )
            }
            "openDoor" -> legacyDoorRelease(string("entityId"), string("service"))
            else -> error("Unsupported stream button action")
        }
    }

    fun legacyDoorRelease(entityId: String, service: String): StreamButtonAction.CallService {
        val name = service.trim().ifBlank { OpenDoorUseCase.defaultServiceFor(entityId) }
        return StreamButtonAction.CallService(
            domain = name.substringBefore('.', ""),
            service = name.substringAfter('.', ""),
            entityId = entityId,
            doorRelease = true
        )
    }

    private fun legacyService(entityId: String, service: String, data: JsonObject = JsonObject()) =
        StreamButtonAction.CallService(entityId.substringBefore('.', ""), service, entityId, data)

    private fun JsonObject.objectOrEmpty(key: String): JsonObject =
        if (has(key)) getAsJsonObject(key) else JsonObject()

    private fun JsonObject.string(key: String): String =
        getAsJsonPrimitive(key).takeIf { it.isString }?.asString
            ?: error("Invalid stream button field: $key")

    private fun JsonObject.optionalString(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.boolean(key: String): Boolean =
        getAsJsonPrimitive(key).takeIf { it.isBoolean }?.asBoolean
            ?: error("Invalid stream button field: $key")

    private val legacyActionTypes = setOf("toggle", "press", "turnOn", "selectOption", "openDoor")
}
