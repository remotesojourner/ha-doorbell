package com.novasoftware.hadoorbell.data.remote

import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var appPreferences: SettingsRepositoryImpl
    private lateinit var authInterceptor: AuthInterceptor
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        appPreferences = mockk()
        authInterceptor = AuthInterceptor(appPreferences)

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `interceptor adds authorization header and overrides host`() = runTest {
        // Arrange
        val expectedToken = "test_token"
        val mockBaseUrl = mockWebServer.url("/").toString()
        
        every { appPreferences.haTokenFlow } returns flowOf(expectedToken)
        every { appPreferences.haUrlFlow } returns flowOf(mockBaseUrl)

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // Act - Requesting a dummy URL which should be overridden by interceptor
        val request = Request.Builder()
            .url("http://dummy.url/api/states/test_entity")
            .build()

        val response = okHttpClient.newCall(request).execute()

        // Assert
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer $expectedToken", recordedRequest.getHeader("Authorization"))
        assertEquals("/api/states/test_entity", recordedRequest.path)
        assertEquals(200, response.code)
    }
}
