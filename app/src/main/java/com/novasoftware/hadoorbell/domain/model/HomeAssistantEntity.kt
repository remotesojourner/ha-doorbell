package com.novasoftware.hadoorbell.domain.model

data class HomeAssistantEntity(
    val entityId: String,
    val name: String,
    val options: List<String> = emptyList(),
    val icon: String? = null
) {
    val domain: String get() = entityId.substringBefore('.', "")
}
