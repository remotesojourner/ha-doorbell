package com.novasoftware.hadoorbell.domain.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class HomeAssistantService(
    val domain: String,
    val service: String,
    val name: String? = null,
    val description: String? = null,
    val fields: Map<String, HomeAssistantServiceField> = emptyMap(),
    val target: JsonObject? = null,
    val needsResponse: Boolean = false
)

data class HomeAssistantServiceField(
    val name: String? = null,
    val description: String? = null,
    val required: Boolean = false,
    val defaultValue: JsonElement? = null,
    val selector: JsonObject? = null
)

private val servicePart = Regex("[a-z0-9_]+")
private val entityId = Regex("[a-z0-9_]+\\.[a-z0-9_]+")

fun isHomeAssistantServicePart(value: String): Boolean = servicePart.matches(value)

fun isHomeAssistantEntityId(value: String): Boolean = entityId.matches(value)

fun isHomeAssistantEventType(value: String): Boolean =
    value.isNotBlank() && value.length <= 64 && value != "." && value != ".." &&
        value.none { it == '/' || it.isISOControl() }
