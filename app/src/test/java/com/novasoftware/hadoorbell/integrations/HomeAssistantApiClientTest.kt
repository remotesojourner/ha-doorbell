package com.novasoftware.hadoorbell.integrations

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HomeAssistantApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: HomeAssistantApiClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val baseUrl = mockWebServer.url("/").toString()
        apiClient = HomeAssistantApiClient(haUrl = baseUrl, token = "test_token")
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getEntityState returns state successfully`() = runTest {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""{"entity_id": "lock.front_door", "state": "locked", "attributes": {}}""")
        mockWebServer.enqueue(mockResponse)

        val state = apiClient.getEntityState("lock.front_door")

        assertEquals("locked", state)
        
        val request = mockWebServer.takeRequest()
        assertEquals("/api/states/lock.front_door", request.path)
        assertEquals("Bearer test_token", request.getHeader("Authorization"))
    }

    @Test
    fun `getEntityState returns unknown when missing state field`() = runTest {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""{"entity_id": "lock.front_door", "attributes": {}}""")
        mockWebServer.enqueue(mockResponse)

        val state = apiClient.getEntityState("lock.front_door")

        assertEquals("unknown", state)
    }

    @Test(expected = Exception::class)
    fun `getEntityState throws exception on HTTP error`() = runTest {
        val mockResponse = MockResponse().setResponseCode(401)
        mockWebServer.enqueue(mockResponse)

        apiClient.getEntityState("lock.front_door")
    }

    @Test
    fun `callService executes successfully`() = runTest {
        val mockResponse = MockResponse().setResponseCode(200).setBody("[]")
        mockWebServer.enqueue(mockResponse)

        apiClient.callService("lock", "unlock", "lock.front_door")

        val request = mockWebServer.takeRequest()
        assertEquals("/api/services/lock/unlock", request.path)
        assertEquals("POST", request.method)
        assertEquals("""{"entity_id":"lock.front_door"}""", request.body.readUtf8())
    }

    @Test
    fun `getSelectOptions returns list successfully`() = runTest {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""{"entity_id": "select.doorbell", "state": "Idle", "attributes": {"options": ["Idle", "Busy", "Leave Package"]}}""")
        mockWebServer.enqueue(mockResponse)

        val options = apiClient.getSelectOptions("select.doorbell")

        assertEquals(3, options.size)
        assertEquals("Leave Package", options[2])
    }

    @Test
    fun `setSelectOption executes successfully`() = runTest {
        val mockResponse = MockResponse().setResponseCode(200).setBody("[]")
        mockWebServer.enqueue(mockResponse)

        apiClient.setSelectOption("select.doorbell", "Busy")

        val request = mockWebServer.takeRequest()
        assertEquals("/api/services/select/select_option", request.path)
        assertEquals("""{"entity_id":"select.doorbell","option":"Busy"}""", request.body.readUtf8())
    }
}
