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
        val requestBuilder = chain.request().newBuilder()

        val token = runBlocking { appPreferences.haTokenFlow.first() }
        val baseUrl = runBlocking { appPreferences.haUrlFlow.first() }

        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        var request = requestBuilder.build()

        if (!baseUrl.isNullOrBlank()) {
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
