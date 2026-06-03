package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetSelectOptionsUseCaseTest {
    private lateinit var repository: HomeAssistantRepository
    private lateinit var getSelectOptionsUseCase: GetSelectOptionsUseCase

    @Before
    fun setup() {
        repository = mockk()
        getSelectOptionsUseCase = GetSelectOptionsUseCase(repository)
    }

    @Test
    fun `invoke returns success with options when repository succeeds`() = runTest {
        // Arrange
        val entityId = "input_select.quick_reply"
        val expectedOptions = listOf("Option 1", "Option 2")
        coEvery { repository.getSelectOptions(entityId) } returns Result.success(expectedOptions)

        // Act
        val result = getSelectOptionsUseCase(entityId)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(expectedOptions, result.getOrNull())
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        // Arrange
        val entityId = "input_select.quick_reply"
        val expectedException = RuntimeException("Network Error")
        coEvery { repository.getSelectOptions(entityId) } returns Result.failure(expectedException)

        // Act
        val result = getSelectOptionsUseCase(entityId)

        // Assert
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }
}
