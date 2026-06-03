package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendQuickReplyUseCaseTest {
    private lateinit var repository: HomeAssistantRepository
    private lateinit var sendQuickReplyUseCase: SendQuickReplyUseCase

    @Before
    fun setup() {
        repository = mockk()
        sendQuickReplyUseCase = SendQuickReplyUseCase(repository)
    }

    @Test
    fun `invoke returns success when repository succeeds`() = runTest {
        // Arrange
        val entityId = "input_select.quick_reply"
        val option = "Hello"
        coEvery { repository.setSelectOption(entityId, option) } returns Result.success(Unit)

        // Act
        val result = sendQuickReplyUseCase(entityId, option)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        // Arrange
        val entityId = "input_select.quick_reply"
        val option = "Hello"
        val expectedException = RuntimeException("Network Error")
        coEvery { repository.setSelectOption(entityId, option) } returns Result.failure(expectedException)

        // Act
        val result = sendQuickReplyUseCase(entityId, option)

        // Assert
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }
}
