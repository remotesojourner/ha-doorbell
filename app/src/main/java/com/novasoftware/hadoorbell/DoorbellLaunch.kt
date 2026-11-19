package com.novasoftware.hadoorbell

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import java.util.concurrent.atomic.AtomicLong

/**
 * A request to open the stream from outside the app, either by the `hadoorbell://stream` and
 * `hadoorbell://answer` deep links or by the ring notification.
 */
data class DoorbellLaunch(val answer: Boolean, val id: Long = nextId.incrementAndGet()) {
    companion object {
        const val SCHEME = "hadoorbell"
        const val HOST_STREAM = "stream"
        const val HOST_ANSWER = "answer"

        private val nextId = AtomicLong()

        fun from(intent: Intent?): DoorbellLaunch? {
            if (intent?.action != Intent.ACTION_VIEW) return null
            val uri = intent.data ?: return null
            if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
            return when (uri.host?.lowercase()) {
                HOST_STREAM -> DoorbellLaunch(answer = false)
                HOST_ANSWER -> DoorbellLaunch(answer = true)
                else -> null
            }
        }

        fun uri(answer: Boolean): Uri = "$SCHEME://${if (answer) HOST_ANSWER else HOST_STREAM}".toUri()

        fun intent(context: Context, answer: Boolean): Intent =
            Intent(Intent.ACTION_VIEW, uri(answer), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
