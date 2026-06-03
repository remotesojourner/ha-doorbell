package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import io.mockk.coEvery
import com.novasoftware.hadoorbell.domain.model.LockState
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnlockDoorUseCaseTest {
    private lateinit var repository: HomeAssistantRepository
    private lateinit var unlockDoorUseCase: UnlockDoorUseCase

    @Before
    fun setup() {
        repository = mockk()
        unlockDoorUseCase = UnlockDoorUseCase(repository)
    }

    @Test
    fun `when door is already unlocked, returns failure`() = runTest {
        // Arrange
        val entityId = "lock.front_door"
        coEvery { repository.getEntityState(entityId) } returns Result.success("unlocked")

        // Act
        val results = unlockDoorUseCase(entityId).toList()

        // Assert
        assertEquals(1, results.size)
        assertTrue(results[0].isFailure)
        assertEquals("Door is already unlocked", results[0].exceptionOrNull()?.message)
    }

    @Test
    fun `when door is locked, calls unlock service and polls until unlocked`() = runTest {
        // Arrange
        val entityId = "lock.front_door"
        // 1st call (preState), 2nd call (poll 1), 3rd call (poll 2)
        coEvery { repository.getEntityState(entityId) } returnsMany listOf(
            Result.success("locked"),
            Result.success("unlocking"),
            Result.success("unlocked")
        )
        coEvery { repository.callService("lock", "unlock", entityId) } returns Result.success(Unit)

        // Act
        val results = unlockDoorUseCase(entityId).toList()

        // Assert
        assertEquals(3, results.size)
        assertEquals(Result.success(LockState.Unlocking), results[0])
        assertEquals(Result.success(LockState.Unlocking), results[1])
        assertEquals(Result.success(LockState.Unlocked), results[2])
    }
}
