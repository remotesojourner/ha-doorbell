package com.novasoftware.hadoorbell.integrations

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

class FrigateSignalingClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var signalingClient: FrigateSignalingClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        signalingClient = FrigateSignalingClient(
            haUrl = baseUrl,
            token = "test_token",
            streamName = "front_door"
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `connect authenticates and connects to stream websocket`() = runBlocking {
        // We need to enqueue a WebSocket response for the auth phase
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Trigger auth_required
                webSocket.send("""{"type": "auth_required"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("auth\"") && text.contains("test_token")) {
                    webSocket.send("""{"type": "auth_ok"}""")
                } else if (text.contains("auth/sign_path")) {
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
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("webrtc/offer")) {
                    webSocket.send("""{"type": "webrtc/answer", "value": "mock_sdp_answer"}""")
                    webSocket.send("""{"type": "webrtc/candidate", "value": "mock_ice_candidate"}""")
                }
            }
        }))

        // Test connecting
        signalingClient.connect()

        // Test sending an offer
        val answer = signalingClient.sendOffer("mock_sdp_offer")
        assertEquals("mock_sdp_answer", answer)

        // Test ICE candidates flow
        val candidate = signalingClient.getIceCandidates().first()
        assertEquals("mock_ice_candidate", candidate)
        
        signalingClient.disconnect()
    }
}
