package com.novasoftware.hadoorbell.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HomeAssistantStateClient @Inject constructor(client: OkHttpClient) {
    private val socketClient = client.newBuilder()
        .pingInterval(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun observe(
        endpoint: HttpUrl,
        token: String,
        entityIds: Set<String>
    ): Flow<Map<String, HomeAssistantEntityAppearance>> = callbackFlow {
        require(entityIds.isNotEmpty()) { "Select Home Assistant entities to observe" }
        val request = try {
            Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .tag(ExplicitHomeAssistantConnection::class.java, ExplicitHomeAssistantConnection)
                .build()
        } catch (_: IllegalArgumentException) {
            throw IOException("Invalid Home Assistant entity subscription connection")
        }
        val auth = JsonObject().apply {
            addProperty("type", "auth")
            addProperty("access_token", token)
        }
        val subscribe = JsonObject().apply {
            addProperty("id", SUBSCRIPTION_ID)
            addProperty("type", "subscribe_entities")
            add("entity_ids", JsonArray().apply { entityIds.forEach { add(it) } })
        }

        val initialSnapshot = CompletableDeferred<Unit>()
        val socket = socketClient.newWebSocket(request, object : WebSocketListener() {
            private var appearances: Map<String, HomeAssistantEntityAppearance>? = null

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = try {
                    JsonParser.parseString(text).objectOrNull()
                } catch (_: JsonParseException) {
                    null
                }
                if (message == null) {
                    close(IOException("Invalid Home Assistant entity subscription message"))
                    return
                }
                when (message.text("type")) {
                    "auth_required" -> send(webSocket, auth)
                    "auth_ok" -> send(webSocket, subscribe)
                    "auth_invalid" -> close(IOException("Home Assistant rejected the entity subscription credentials"))
                    "result" -> if (message.isSubscription() && message.get("success") != JsonPrimitive(true)) {
                        close(IOException("Home Assistant rejected the entity subscription"))
                    }
                    "event" -> if (message.isSubscription()) {
                        val event = message.get("event").objectOrNull()
                        if (event == null) {
                            close(IOException("Invalid Home Assistant entity subscription event"))
                        } else {
                            onEntityEvent(event)
                        }
                    }
                }
            }

            private fun send(webSocket: WebSocket, message: JsonObject) {
                if (!webSocket.send(message.toString())) {
                    close(IOException("Home Assistant entity subscription could not send a message"))
                }
            }

            private fun onEntityEvent(event: JsonObject) {
                val previous = appearances
                if (previous == null && event.get("a").objectOrNull() == null) {
                    close(IOException("Home Assistant did not send the initial entity snapshot"))
                    return
                }
                val updated = updateAppearances(previous.orEmpty(), event, entityIds)
                if (previous == updated) return
                if (trySend(updated).isFailure) {
                    close(IOException("Home Assistant entity subscription fell behind"))
                    return
                }
                appearances = updated
                initialSnapshot.complete(Unit)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                close(IOException("Home Assistant entity subscription closed"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(IOException("Home Assistant entity subscription disconnected", t))
            }
        })

        val handshake = launch {
            if (withTimeoutOrNull(INITIAL_SNAPSHOT_TIMEOUT_MS) { initialSnapshot.await() } == null) {
                close(IOException("Timed out waiting for Home Assistant entity states"))
            }
        }
        awaitClose {
            handshake.cancel()
            socket.cancel()
        }
    }

    private fun updateAppearances(
        previous: Map<String, HomeAssistantEntityAppearance>,
        event: JsonObject,
        entityIds: Set<String>
    ): Map<String, HomeAssistantEntityAppearance> {
        val updated = previous.toMutableMap()
        event.get("a").objectOrNull()?.entrySet()?.forEach { (entityId, value) ->
            if (entityId !in entityIds) return@forEach
            val added = value.objectOrNull() ?: return@forEach
            val state = added.text("s") ?: return@forEach
            val attributes = added.get("a").objectOrNull()
            updated[entityId] = HomeAssistantEntityAppearance(
                state,
                attributes?.attribute("icon"),
                attributes?.attribute("device_class")
            )
        }
        event.get("c").objectOrNull()?.entrySet()?.forEach { (entityId, value) ->
            val appearance = updated[entityId] ?: return@forEach
            val patch = value.objectOrNull() ?: return@forEach
            val additions = patch.get("+").objectOrNull()
            val attributes = additions?.get("a").objectOrNull()
            val removed = patch.get("-").objectOrNull()?.get("a").arrayOrNull()
            updated[entityId] = HomeAssistantEntityAppearance(
                state = additions?.text("s") ?: appearance.state,
                icon = mergeAttribute("icon", appearance.icon, attributes, removed),
                deviceClass = mergeAttribute("device_class", appearance.deviceClass, attributes, removed)
            )
        }
        event.get("r").arrayOrNull()?.forEach { entityId ->
            entityId.stringOrNull()?.let { updated.remove(it) }
        }
        return updated
    }

    private fun mergeAttribute(key: String, previous: String?, additions: JsonObject?, removed: JsonArray?): String? =
        when {
            removed?.contains(JsonPrimitive(key)) == true -> null
            additions?.has(key) == true -> additions.attribute(key)
            else -> previous
        }

    private fun JsonObject.attribute(key: String): String? = text(key)?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.isSubscription(): Boolean = get("id") == JsonPrimitive(SUBSCRIPTION_ID)

    private fun JsonObject.text(key: String): String? = get(key).stringOrNull()

    private fun JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.asString

    private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

    private companion object {
        const val SUBSCRIPTION_ID = 1
        const val INITIAL_SNAPSHOT_TIMEOUT_MS = 12_000L
    }
}
