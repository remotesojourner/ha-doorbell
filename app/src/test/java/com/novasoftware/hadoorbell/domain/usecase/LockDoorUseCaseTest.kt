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

class LockDoorUseCaseTest {
    private lateinit var repository: HomeAssistantRepository
    private lateinit var lockDoorUseCase: LockDoorUseCase

    @Before
    fun setup() {
        repository = mockk()
        lockDoorUseCase = LockDoorUseCase(repository)
    }

    @Test
    fun `when door is already locked, returns failure`() = runTest {
        // Arrange
        val entityId = "lock.front_door"
        coEvery { repository.getEntityState(entityId) } returns Result.success("locked")

        // Act
        val results = lockDoorUseCase(entityId).toList()

        // Assert
        assertEquals(1, results.size)
        assertTrue(results[0].isFailure)
        assertEquals("Door is already locked", results[0].exceptionOrNull()?.message)
    }

    @Test
    fun `when door is unlocked, calls lock service and polls until locked`() = runTest {
        // Arrange
        val entityId = "lock.front_door"
        // 1st call (preState), 2nd call (poll 1), 3rd call (poll 2)
        coEvery { repository.getEntityState(entityId) } returnsMany listOf(
            Result.success("unlocked"),
            Result.success("locking"),
            Result.success("locked")
        )
        coEvery { repository.callService("lock", "lock", entityId) } returns Result.success(Unit)

        // Act
        val results = lockDoorUseCase(entityId).toList()

        // Assert
        assertEquals(3, results.size)
        assertEquals(Result.success(LockState.Locking), results[0])
        assertEquals(Result.success(LockState.Locking), results[1])
        assertEquals(Result.success(LockState.Locked), results[2])
    }
}
