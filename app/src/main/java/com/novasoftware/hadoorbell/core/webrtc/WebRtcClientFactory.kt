package com.novasoftware.hadoorbell.core.webrtc

import com.google.gson.Gson
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.data.remote.HomeAssistantWebRtcClient
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRtcClientFactory @Inject constructor(
    private val appPreferences: SettingsRepositoryImpl,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun createClient(): HomeAssistantWebRtcClient {
        val url = appPreferences.haUrlFlow.first() ?: ""
        val token = appPreferences.haTokenFlow.first() ?: ""
        val streamSource = appPreferences.streamSourceFlow.first() ?: ""
        val provider = appPreferences.webrtcProviderFlow.first()
        return HomeAssistantWebRtcClient(url, token, streamSource, provider, okHttpClient, gson)
    }
}
