package com.novasoftware.hadoorbell.core.webrtc

import android.content.Context
import com.novasoftware.hadoorbell.data.remote.HomeAssistantWebRtcClient
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class WebRtcManagerFactory @Inject constructor() {
    fun create(
        context: Context,
        signalingClient: HomeAssistantWebRtcClient,
        coroutineScope: CoroutineScope
    ): WebRtcManager {
        return WebRtcManager(context, signalingClient, coroutineScope)
    }
}
