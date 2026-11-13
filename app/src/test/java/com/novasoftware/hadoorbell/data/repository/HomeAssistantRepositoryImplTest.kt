package com.novasoftware.hadoorbell.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novasoftware.hadoorbell.data.remote.DraftHomeAssistantConnection
import com.novasoftware.hadoorbell.data.remote.EntityStateResponse
import com.novasoftware.hadoorbell.data.remote.HomeAssistantApi
import com.novasoftware.hadoorbell.data.remote.ServiceDomainResponse
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantRepositoryImplTest {

    private lateinit var api: HomeAssistantApi
    private lateinit var repository: HomeAssistantRepositoryImpl

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        repository = HomeAssistantRepositoryImpl(api)
    }

    @Test
    fun `getEntities uses the draft server and maps friendly names options and icons`() = runTest {
        coEvery {
            api.getEntities(
                "https://draft.home:8123/api/states".toHttpUrl(),
                "Bearer draft_token",
                DraftHomeAssistantConnection
            )
        } returns listOf(
            EntityStateResponse(
                "select.reply", "Go away",
                mapOf(
                    "friendly_name" to "Doorbell replies",
                    "options" to listOf("Go away", "Leave at door"),
                    "icon" to "mdi:message-text"
                )
            ),
            EntityStateResponse("lock.front_door", "locked", null)
        )

        val entities = repository.getEntities(" http://draft.home:8123/ ", " draft_token ").getOrThrow()

        assertEquals(
            listOf(
                HomeAssistantEntity(
                    "select.reply", "Doorbell replies", listOf("Go away", "Leave at door"),
                    icon = "mdi:message-text",
                    state = "Go away"
                ),
                HomeAssistantEntity("lock.front_door", "lock.front_door", state = "locked")
            ),
            entities
        )
    }

    @Test
    fun `live entity appearance includes its state and optional icon metadata`() = runTest {
        coEvery { api.getEntityState("light.porch") } returns
            EntityStateResponse("light.porch", "on", mapOf("icon" to "mdi:outdoor-lamp"))
        coEvery { api.getEntityState("switch.relay") } returns
            EntityStateResponse("switch.relay", "off", mapOf("device_class" to "outlet"))

        assertEquals(
            HomeAssistantEntityAppearance("on", "mdi:outdoor-lamp"),
            repository.getEntityAppearance("light.porch").getOrThrow()
        )
        assertEquals(
            HomeAssistantEntityAppearance("off", deviceClass = "outlet"),
            repository.getEntityAppearance("switch.relay").getOrThrow()
        )
    }

    @Test
    fun `getEntities rejects malformed or incomplete connection settings before network access`() = runTest {
        assertTrue(repository.getEntities("ftp://draft.home", "token").isFailure)
        assertTrue(repository.getEntities("https://", "token").isFailure)
        assertTrue(repository.getEntities("https://user:password@draft.home", "token").isFailure)
        assertTrue(repository.getEntities("https://draft.home", " ").isFailure)
        coVerify(exactly = 0) { api.getEntities(any(), any(), any()) }
    }

    @Test
    fun `service discovery reads the catalog from the draft connection`() = runTest {
        val serviceData = JsonParser.parseString("""
            {
              "get_forecasts": {
                "name": "Get forecasts",
                "fields": {"type": {"required": true, "selector": {"select": {"options": ["daily"]}}}},
                "target": {"entity": [{"domain": ["weather"]}]},
                "response": {"optional": false}
              }
            }
        """).asJsonObject
        coEvery {
            api.getServices(
                "https://draft.home:8123/api/services".toHttpUrl(),
                "Bearer draft_token",
                DraftHomeAssistantConnection
            )
        } returns listOf(ServiceDomainResponse("weather", serviceData))

        val service = repository.getServices("draft.home:8123", " draft_token ").getOrThrow().single()

        assertEquals("weather", service.domain)
        assertEquals("get_forecasts", service.service)
        assertEquals("Get forecasts", service.name)
        assertTrue(service.needsResponse)
        assertTrue(service.fields.getValue("type").required)
        assertTrue(service.fields.getValue("type").selector!!.has("select"))
        assertTrue(service.target!!.has("entity"))
    }

    @Test
    fun `service calls keep structured fields and add the selected entity to the payload`() = runTest {
        val data = JsonObject().apply {
            addProperty("temperature", 22)
            addProperty("enabled", true)
        }
        val expected = data.deepCopy().apply { addProperty("entity_id", "climate.kitchen") }
        coEvery { api.callService("climate", "set_temperature", expected, true) } returns Unit

        repository.callService("climate", "set_temperature", "climate.kitchen", data, needsResponse = true)
            .getOrThrow()

        coVerify(exactly = 1) { api.callService("climate", "set_temperature", expected, true) }
        assertTrue(!data.has("entity_id"))
    }

    @Test
    fun `getEntityState returns success when API call succeeds`() = runTest {
        coEvery { api.getEntityState(any()) } returns EntityStateResponse("lock.front_door", "locked", null)
        
        val result = repository.getEntityState("lock.front_door")
        
        assertTrue(result.isSuccess)
        assertEquals("locked", result.getOrNull())
    }

    @Test
    fun `getEntityState returns failure when API call throws exception`() = runTest {
        val exception = RuntimeException("Network Error")
        coEvery { api.getEntityState(any()) } throws exception
        
        val result = repository.getEntityState("lock.front_door")
        
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `callService returns success when API call succeeds`() = runTest {
        coEvery { api.callService(any(), any(), any(), any()) } returns Unit
        
        val result = repository.callService("lock", "unlock", "lock.front_door")
        
        assertTrue(result.isSuccess)
    }

    @Test
    fun `callService returns failure when API call throws exception`() = runTest {
        val exception = RuntimeException("Service Error")
        coEvery { api.callService(any(), any(), any(), any()) } throws exception
        
        val result = repository.callService("lock", "unlock", "lock.front_door")
        
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `getSelectOptions returns success when API call succeeds`() = runTest {
        val expectedOptions = listOf("Option 1", "Option 2")
        coEvery { api.getEntityState(any()) } returns EntityStateResponse("input_select.qr", "Option 1", mapOf("options" to expectedOptions))
        
        val result = repository.getSelectOptions("input_select.qr")
        
        assertTrue(result.isSuccess)
        assertEquals(expectedOptions, result.getOrNull())
    }

    @Test
    fun `getSelectOptions returns failure when API call throws exception`() = runTest {
        val exception = RuntimeException("Options Error")
        coEvery { api.getEntityState(any()) } throws exception
        
        val result = repository.getSelectOptions("input_select.qr")
        
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `setSelectOption returns success when API call succeeds`() = runTest {
        coEvery { api.setSelectOption(any(), any()) } returns Unit
        
        val result = repository.setSelectOption("input_select.qr", "Option 1")
        
        assertTrue(result.isSuccess)
    }

    @Test
    fun `setSelectOption returns failure when API call throws exception`() = runTest {
        val exception = RuntimeException("Set Option Error")
        coEvery { api.setSelectOption(any(), any()) } throws exception
        
        val result = repository.setSelectOption("input_select.qr", "Option 1")
        
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
