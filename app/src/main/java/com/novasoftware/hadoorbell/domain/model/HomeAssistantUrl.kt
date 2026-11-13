package com.novasoftware.hadoorbell.domain.model

internal fun normalizeHomeAssistantUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return trimmed
    return when {
        trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substringAfter("://").trimEnd('/')
        "://" in trimmed -> trimmed
        else -> "https://" + trimmed.trimEnd('/')
    }
}
