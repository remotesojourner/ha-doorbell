package com.novasoftware.hadoorbell.data.remote

import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val appPreferences: SettingsRepositoryImpl
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.tag(ExplicitHomeAssistantConnection::class.java) != null) {
            return chain.proceed(originalRequest)
        }
        val requestBuilder = originalRequest.newBuilder()

        val (baseUrl, token) = runBlocking { appPreferences.homeAssistantConnectionFlow.first() }

        if (token.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        var request = requestBuilder.build()

        if (baseUrl.isNotBlank()) {
            val parsedBaseUrl = baseUrl.toHttpUrlOrNull()
            if (parsedBaseUrl != null) {
                val newUrl = request.url.newBuilder()
                    .scheme(parsedBaseUrl.scheme)
                    .host(parsedBaseUrl.host)
                    .port(parsedBaseUrl.port)
                    // The path is already set by Retrofit interface annotations
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }
        }

        return chain.proceed(request)
    }
}
