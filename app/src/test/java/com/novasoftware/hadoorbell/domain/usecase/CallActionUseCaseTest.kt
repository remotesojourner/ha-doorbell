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

class CallActionUseCaseTest {

    private lateinit var repository: HomeAssistantRepository
    private lateinit var callActionUseCase: CallActionUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        callActionUseCase = CallActionUseCase(repository)
    }

    @Test
    fun `splits the service into domain and name`() = runTest {
        val result = callActionUseCase("switch.turn_on", "switch.doorbell_talk")

        assertTrue(result.isSuccess)
        coVerify { repository.callService("switch", "turn_on", "switch.doorbell_talk") }
    }

    @Test
    fun `accepts services without a target entity`() = runTest {
        val result = callActionUseCase("script.doorbell_answer")

        assertTrue(result.isSuccess)
        coVerify { repository.callService("script", "doorbell_answer", null) }
    }

    @Test
    fun `trims surrounding whitespace`() = runTest {
        val result = callActionUseCase("  lock.unlock  ", "  lock.front_door  ")

        assertTrue(result.isSuccess)
        coVerify { repository.callService("lock", "unlock", "lock.front_door") }
    }

    @Test
    fun `rejects services without a domain`() = runTest {
        val result = callActionUseCase("unlock")

        assertTrue(result.isFailure)
        assertEquals("Invalid action 'unlock', expected domain.service", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { repository.callService(any(), any(), any()) }
    }

    @Test
    fun `propagates Home Assistant failures`() = runTest {
        coEvery { repository.callService(any(), any(), any()) } returns Result.failure(RuntimeException("boom"))

        val result = callActionUseCase("switch.turn_on", "switch.doorbell_talk")

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }
}
