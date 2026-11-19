package com.novasoftware.hadoorbell.ring

import android.os.Bundle

/** What Home Assistant asked the ring to show, read from the broadcast's extras. */
data class RingRequest(
    val title: String? = null,
    val message: String? = null,
    val image: String? = null,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(EXTRA_TITLE, title)
        putString(EXTRA_MESSAGE, message)
        putString(EXTRA_IMAGE, image)
        putInt(EXTRA_TIMEOUT, timeoutSeconds)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_IMAGE = "image"
        const val EXTRA_TIMEOUT = "timeout"
        const val DEFAULT_TIMEOUT_SECONDS = 45
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 600
        private const val MAX_TEXT_LENGTH = 200

        fun from(extras: Bundle?): RingRequest {
            fun text(key: String): String? =
                extras?.getString(key)?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_TEXT_LENGTH)

            @Suppress("DEPRECATION")
            val timeout = when (val value = extras?.get(EXTRA_TIMEOUT)) {
                is Number -> value.toInt()
                is String -> value.trim().toIntOrNull()
                else -> null
            }
            return RingRequest(
                title = text(EXTRA_TITLE),
                message = text(EXTRA_MESSAGE),
                image = text(EXTRA_IMAGE)?.takeIf(::isHomeAssistantPath),
                timeoutSeconds = (timeout ?: DEFAULT_TIMEOUT_SECONDS).coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
            )
        }

        /** Snapshots are only loaded from the configured Home Assistant server, so only relative paths are accepted. */
        fun isHomeAssistantPath(path: String): Boolean = path.startsWith("/") && !path.startsWith("//")
    }
}
