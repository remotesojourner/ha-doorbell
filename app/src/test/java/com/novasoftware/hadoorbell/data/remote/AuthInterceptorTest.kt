package com.novasoftware.hadoorbell.data.remote

import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.model.HomeAssistantConnection
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
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
        
        every { appPreferences.homeAssistantConnectionFlow } returns
            flowOf(HomeAssistantConnection(mockBaseUrl, expectedToken))

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

    @Test
    fun `draft discovery keeps the explicitly provided destination and authorization`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(
            """[{"entity_id":"switch.relay","state":"off","attributes":{"friendly_name":"Relay"}}]"""
        ))
        val api = Retrofit.Builder()
            .baseUrl("http://dummy.url/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HomeAssistantApi::class.java)

        val entities = api.getEntities(
            mockWebServer.url("/api/states"), "Bearer draft_token", ExplicitHomeAssistantConnection
        )

        val recordedRequest = checkNotNull(mockWebServer.takeRequest(5, TimeUnit.SECONDS))
        assertEquals(listOf("Bearer draft_token"), recordedRequest.headers.values("Authorization"))
        assertEquals("/api/states", recordedRequest.path)
        assertEquals(
            listOf(EntityStateResponse("switch.relay", "off", mapOf("friendly_name" to "Relay"))),
            entities
        )
    }
}
