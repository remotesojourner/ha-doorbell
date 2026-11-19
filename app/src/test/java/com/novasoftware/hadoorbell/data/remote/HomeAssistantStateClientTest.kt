package com.novasoftware.hadoorbell.data.remote

import com.google.gson.JsonParser
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class HomeAssistantStateClientTest {
    private val client = HomeAssistantStateClient(OkHttpClient())

    @Test
    fun `filtered subscription merges partial state and attribute changes and removes deleted entities`() {
        MockWebServer().use { server ->
            val credentials = CompletableDeferred<String>()
            val subscription = CompletableDeferred<String>()
            val disconnected = CompletableDeferred<Unit>()
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"auth_required"}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = JsonParser.parseString(text).asJsonObject
                    when (message["type"].asString) {
                        "auth" -> {
                            credentials.complete(message["access_token"].asString)
                            webSocket.send("""{"type":"auth_ok"}""")
                        }
                        "subscribe_entities" -> {
                            subscription.complete(text)
                            webSocket.send("""{"id":1,"type":"result","success":true,"result":null}""")
                            webSocket.send(
                                """{"id":1,"type":"event","event":{"a":{"switch.porch":{"s":"off","a":{"device_class":"outlet","icon":"mdi:power-plug"}},"light.unrelated":{"s":"on"}}}}"""
                            )
                            webSocket.send(
                                """{"id":2,"type":"event","event":{"a":{"lock.front":{"s":"locked"}}}}"""
                            )
                            webSocket.send(
                                """{"id":1,"type":"event","event":{"c":{"switch.porch":{"+":{"s":"on"}}}}}"""
                            )
                            webSocket.send(
                                """{"id":1,"type":"event","event":{"c":{"switch.porch":{"+":{"a":{"device_class":"switch"}},"-":{"a":["icon"]}}}}}"""
                            )
                            webSocket.send("""{"id":1,"type":"event","event":{"r":["switch.porch"]}}""")
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    disconnected.complete(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    disconnected.complete(Unit)
                }
            }))

            val snapshots = runBlocking {
                withTimeout(5_000L) {
                    val snapshots = client.observe(
                        server.url("/api/websocket"),
                        "test_token",
                        setOf("switch.porch", "lock.front")
                    ).take(4).toList()
                    assertEquals("test_token", credentials.await())
                    val request = JsonParser.parseString(subscription.await()).asJsonObject
                    assertEquals(
                        setOf("switch.porch", "lock.front"),
                        request.getAsJsonArray("entity_ids").map { it.asString }.toSet()
                    )
                    disconnected.await()
                    snapshots
                }
            }

            assertEquals(
                listOf(
                    mapOf("switch.porch" to HomeAssistantEntityAppearance("off", "mdi:power-plug", "outlet")),
                    mapOf("switch.porch" to HomeAssistantEntityAppearance("on", "mdi:power-plug", "outlet")),
                    mapOf("switch.porch" to HomeAssistantEntityAppearance("on", deviceClass = "switch")),
                    emptyMap()
                ),
                snapshots
            )
            val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/api/websocket", request.path)
            assertEquals("Bearer test_token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `an empty initial snapshot establishes the subscription`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"auth_required"}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = JsonParser.parseString(text).asJsonObject
                    when (message["type"].asString) {
                        "auth" -> webSocket.send("""{"type":"auth_ok"}""")
                        "subscribe_entities" -> {
                            webSocket.send("""{"id":1,"type":"result","success":true}""")
                            webSocket.send("""{"id":1,"type":"event","event":{"a":{}}}""")
                        }
                    }
                }
            }))
            val initial = runBlocking {
                withTimeout(5_000L) {
                    client.observe(server.url("/api/websocket"), "test_token", setOf("light.missing")).first()
                }
            }
            assertTrue(initial.isEmpty())
        }
    }

    @Test
    fun `rejected authentication fails the subscription instead of publishing an empty state`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"auth_required"}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    webSocket.send("""{"type":"auth_invalid","message":"Invalid access token"}""")
                }
            }))
            val failure = runCatching {
                runBlocking {
                    withTimeout(5_000L) {
                        client.observe(server.url("/api/websocket"), "test_token", setOf("lock.front")).first()
                    }
                }
            }.exceptionOrNull()

            assertTrue(failure is IOException)
        }
    }

    @Test
    fun `an invalid authorization header fails without including the token`() {
        val failure = runCatching {
            runBlocking {
                client.observe(
                    okhttp3.HttpUrl.Builder().scheme("https").host("home.local").build(),
                    "invalid\ntoken",
                    setOf("lock.front")
                ).first()
            }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("Invalid Home Assistant entity subscription connection", failure?.message)
        assertTrue(generateSequence(failure) { it.cause }.all {
            it.message == "Invalid Home Assistant entity subscription connection"
        })
    }
}
