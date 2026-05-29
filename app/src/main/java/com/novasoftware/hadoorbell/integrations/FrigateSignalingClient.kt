package com.novasoftware.hadoorbell.integrations

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FrigateSignalingClient(
    private val haUrl: String,
    private val token: String,
    private val streamName: String
) {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var pendingOfferContinuation: kotlin.coroutines.Continuation<String>? = null
    private val iceCandidatesFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 100)

    private suspend fun getSignedUrl(): String = suspendCancellableCoroutine { continuation ->
        val wsUrl = haUrl.trimEnd('/') + "/api/websocket"
        val request = Request.Builder().url(wsUrl).build()
        val tempClient = OkHttpClient()
        var socket: WebSocket? = null
        
        socket = tempClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val map = gson.fromJson(text, Map::class.java)
                    val type = map["type"] as? String
                    
                    if (type == "auth_required") {
                        val authMsg = mapOf("type" to "auth", "access_token" to token)
                        webSocket.send(gson.toJson(authMsg))
                    } else if (type == "auth_ok") {
                        val path = "/api/frigate/frigate/mse/api/ws?src=$streamName"
                        val signMsg = mapOf(
                            "id" to 1,
                            "type" to "auth/sign_path",
                            "path" to path,
                            "expires" to 300
                        )
                        webSocket.send(gson.toJson(signMsg))
                    } else if (type == "auth_invalid") {
                        if (continuation.isActive) continuation.resumeWithException(Exception("Auth invalid"))
                        socket?.close(1000, null)
                    } else if (type == "result") {
                        val success = map["success"] as? Boolean ?: false
                        if (success) {
                            val result = map["result"] as? Map<*, *>
                            val signedPath = result?.get("path") as? String
                            if (signedPath != null) {
                                val fullUrl = haUrl.trimEnd('/') + signedPath
                                if (continuation.isActive) continuation.resume(fullUrl)
                                socket?.close(1000, null)
                            }
                        } else {
                            val error = map["error"] as? Map<*, *>
                            val msg = error?.get("message") as? String ?: "Failed to sign path"
                            if (continuation.isActive) continuation.resumeWithException(Exception(msg))
                            socket?.close(1000, null)
                        }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (continuation.isActive) continuation.resumeWithException(t)
            }
        })
    }

    suspend fun connect(): Unit = suspendCancellableCoroutine { continuation ->
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val signedUrl = getSignedUrl()
                
                val request = Request.Builder()
                    .url(signedUrl)
                    .build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val map = gson.fromJson(text, Map::class.java)
                            val type = map["type"] as? String
                            
                            if (type == "webrtc/answer") {
                                val value = map["value"] as? String
                                if (value != null) {
                                    pendingOfferContinuation?.resume(value)
                                    pendingOfferContinuation = null
                                } else {
                                    pendingOfferContinuation?.resumeWithException(Exception("Empty answer"))
                                    pendingOfferContinuation = null
                                }
                            } else if (type == "webrtc/candidate") {
                                val value = map["value"] as? String
                                if (value != null) {
                                    iceCandidatesFlow.tryEmit(value)
                                }
                            } else if (type == "error") {
                                val error = map["value"] as? String ?: "Unknown go2rtc error"
                                pendingOfferContinuation?.resumeWithException(Exception(error))
                                pendingOfferContinuation = null
                            }
                        } catch (e: Exception) {
                            Log.e("FrigateSignaling", "Error parsing message", e)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e("FrigateSignaling", "WebSocket failure", t)
                        if (continuation.isActive) continuation.resumeWithException(t)
                        pendingOfferContinuation?.resumeWithException(t)
                        pendingOfferContinuation = null
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    }
                })
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
        
        continuation.invokeOnCancellation {
            webSocket?.cancel()
        }
    }

    suspend fun sendOffer(sdp: String): String = suspendCancellableCoroutine { continuation ->
        pendingOfferContinuation = continuation
        val msg = mapOf(
            "type" to "webrtc/offer",
            "value" to sdp
        )
        webSocket?.send(gson.toJson(msg))
    }

    fun getIceCandidates(): Flow<String> = iceCandidatesFlow

    fun disconnect() {
        webSocket?.close(1000, "Disconnected by user")
    }
}
