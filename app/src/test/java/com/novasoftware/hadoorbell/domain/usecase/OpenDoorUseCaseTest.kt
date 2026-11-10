package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenDoorUseCaseTest {

    private lateinit var repository: HomeAssistantRepository
    private lateinit var openDoorUseCase: OpenDoorUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        openDoorUseCase = OpenDoorUseCase(CallActionUseCase(repository))
    }

    @Test
    fun `explicit service is called with the configured entity`() = runTest {
        coEvery { repository.callService("switch", "turn_on", "switch.door_relay") } returns Result.success(Unit)

        val result = openDoorUseCase("switch.door_relay", "switch.turn_on")

        assertTrue(result.isSuccess)
        coVerify { repository.callService("switch", "turn_on", "switch.door_relay") }
    }

    @Test
    fun `switch entity without a service defaults to switch turn_on`() = runTest {
        coEvery { repository.callService("switch", "turn_on", "switch.door_relay") } returns Result.success(Unit)

        val result = openDoorUseCase("switch.door_relay")

        assertTrue(result.isSuccess)
        coVerify { repository.callService("switch", "turn_on", "switch.door_relay") }
    }

    @Test
    fun `lock entity without a service defaults to lock unlock`() = runTest {
        coEvery { repository.callService("lock", "unlock", "lock.front_door") } returns Result.success(Unit)

        val result = openDoorUseCase("lock.front_door", "")

        assertTrue(result.isSuccess)
        coVerify { repository.callService("lock", "unlock", "lock.front_door") }
    }

    @Test
    fun `button entity without a service defaults to button press`() = runTest {
        coEvery { repository.callService("button", "press", "button.doorbell") } returns Result.success(Unit)

        val result = openDoorUseCase("button.doorbell")

        assertTrue(result.isSuccess)
        coVerify { repository.callService("button", "press", "button.doorbell") }
    }

    @Test
    fun `blank entity fails without calling Home Assistant`() = runTest {
        val result = openDoorUseCase("", "switch.turn_on")

        assertTrue(result.isFailure)
        assertEquals("No door entity configured", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { repository.callService(any(), any(), any()) }
    }

    @Test
    fun `unknown domain without a service fails with a configuration error`() = runTest {
        val result = openDoorUseCase("sensor.door")

        assertTrue(result.isFailure)
        assertEquals("No door service configured for 'sensor.door'", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { repository.callService(any(), any(), any()) }
    }

    @Test
    fun `repository failures are propagated`() = runTest {
        coEvery { repository.callService(any(), any(), any()) } returns Result.failure(RuntimeException("offline"))

        val result = openDoorUseCase("switch.door_relay", "switch.turn_on")

        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }
}
