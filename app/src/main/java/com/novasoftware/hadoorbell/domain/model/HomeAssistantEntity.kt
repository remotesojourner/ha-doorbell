package com.novasoftware.hadoorbell.domain.model

data class HomeAssistantEntity(
    val entityId: String,
    val name: String,
    val options: List<String> = emptyList(),
    val icon: String? = null,
    val state: String? = null,
    val deviceClass: String? = null
) {
    val domain: String get() = entityId.substringBefore('.', "")
    val appearance: HomeAssistantEntityAppearance
        get() = HomeAssistantEntityAppearance(state.orEmpty(), icon, deviceClass)
}

data class HomeAssistantEntityAppearance(
    val state: String,
    val icon: String? = null,
    val deviceClass: String? = null
)
