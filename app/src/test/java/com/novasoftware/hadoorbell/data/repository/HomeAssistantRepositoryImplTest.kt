package com.novasoftware.hadoorbell.data.repository

import com.novasoftware.hadoorbell.data.remote.DraftHomeAssistantConnection
import com.novasoftware.hadoorbell.data.remote.EntityStateResponse
import com.novasoftware.hadoorbell.data.remote.HomeAssistantApi
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                    icon = "mdi:message-text"
                ),
                HomeAssistantEntity("lock.front_door", "lock.front_door")
            ),
            entities
        )
    }

    @Test
    fun `live entity icon comes from state attributes when available`() = runTest {
        coEvery { api.getEntityState("light.porch") } returns
            EntityStateResponse("light.porch", "on", mapOf("icon" to "mdi:outdoor-lamp"))
        coEvery { api.getEntityState("switch.relay") } returns EntityStateResponse("switch.relay", "off", null)

        assertEquals("mdi:outdoor-lamp", repository.getEntityIcon("light.porch").getOrThrow())
        assertNull(repository.getEntityIcon("switch.relay").getOrThrow())
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
        coEvery { api.callService(any(), any(), any()) } returns Unit
        
        val result = repository.callService("lock", "unlock", "lock.front_door")
        
        assertTrue(result.isSuccess)
    }

    @Test
    fun `callService returns failure when API call throws exception`() = runTest {
        val exception = RuntimeException("Service Error")
        coEvery { api.callService(any(), any(), any()) } throws exception
        
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
