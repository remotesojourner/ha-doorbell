package com.novasoftware.hadoorbell.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HomeAssistantWebRtcClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var signalingClient: HomeAssistantWebRtcClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        signalingClient = HomeAssistantWebRtcClient(
            haUrl = baseUrl,
            token = "test_token",
            streamName = "front_door"
        )
    }

    @After
    fun teardown() {
        try {
            mockWebServer.shutdown()
        } catch (_: Throwable) {
            // MockWebServer sometimes throws "Gave up waiting for queue to shut down"
            // if WebSockets are still partially open during teardown.
        }
    }

    @Test
    fun `connect authenticates and connects to stream websocket using default frigate provider`() = runBlocking {
        var isPathCorrect = false
        // We need to enqueue a WebSocket response for the auth phase
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("TEST: First websocket onOpen")
                // Trigger auth_required
                webSocket.send("""{"type": "auth_required"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("TEST: First websocket received message: $text")
                if (text.contains("auth\"") && text.contains("test_token")) {
                    webSocket.send("""{"type": "auth_ok"}""")
                } else if (text.contains("auth/sign_path")) {
                    isPathCorrect = text.contains("api/frigate/frigate/mse/api/ws")
                    // Send back a successful signed path pointing to a second mock websocket
                    webSocket.send("""
                        {
                            "id": 1,
                            "type": "result",
                            "success": true,
                            "result": { "path": "/api/stream/ws" }
                        }
                    """.trimIndent())
                }
            }
        }))

        // Enqueue the second WebSocket response for the actual stream
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("TEST: Second websocket onOpen")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                println("TEST: Second websocket received message: $text")
                if (text.contains("webrtc/offer")) {
                    webSocket.send("""{"type": "webrtc/answer", "value": "mock_sdp_answer"}""")
                    webSocket.send("""{"type": "webrtc/candidate", "value": "mock_ice_candidate"}""")
                }
            }
        }))

        // Test connecting
        println("TEST: Calling connect()")
        signalingClient.connect()
        println("TEST: connect() returned")

        // Test sending an offer and capturing the ICE candidate simultaneously
        println("TEST: Async waiting for candidate")
        val candidateDeferred = async { signalingClient.getIceCandidates().first() }
        
        println("TEST: Calling sendOffer")
        val answer = signalingClient.sendOffer("mock_sdp_offer")
        println("TEST: sendOffer returned: $answer")
        assertEquals("mock_sdp_answer", answer)

        // Verify ICE candidates flow received the candidate
        println("TEST: Awaiting candidateDeferred")
        val candidate = candidateDeferred.await()
        println("TEST: candidateDeferred returned: $candidate")
        assertEquals("mock_ice_candidate", candidate)
        
        signalingClient.disconnect()
        
        // Assert the correct path was requested
        assert(isPathCorrect)
    }

    @Test
    fun `connect authenticates and connects to stream websocket using webrtc provider`() = runBlocking {
        var isPathCorrect = false
        val webrtcClient = HomeAssistantWebRtcClient(
            haUrl = mockWebServer.url("/").toString(),
            token = "test_token",
            streamName = "front_door",
            provider = "webrtc"
        )

        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type": "auth_required"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("auth\"") && text.contains("test_token")) {
                    webSocket.send("""{"type": "auth_ok"}""")
                } else if (text.contains("auth/sign_path")) {
                    isPathCorrect = text.contains("api/webrtc/ws")
                    webSocket.send("""
                        {
                            "id": 1,
                            "type": "result",
                            "success": true,
                            "result": { "path": "/api/stream/ws" }
                        }
                    """.trimIndent())
                }
            }
        }))

        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {}
        }))

        webrtcClient.connect()
        webrtcClient.disconnect()

        // Assert the correct path was requested
        assert(isPathCorrect)
    }
}
