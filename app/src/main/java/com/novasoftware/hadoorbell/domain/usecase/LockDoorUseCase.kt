package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.novasoftware.hadoorbell.domain.model.LockState
import javax.inject.Inject

class LockDoorUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    operator fun invoke(entityId: String): Flow<Result<LockState>> = flow {
        // Fetch current state
        val preStateResult = repository.getEntityState(entityId)
        if (preStateResult.isFailure) {
            emit(Result.failure(preStateResult.exceptionOrNull()!!))
            return@flow
        }

        val preState = LockState.fromString(preStateResult.getOrNull())
        if (preState == LockState.Locked) {
            emit(Result.failure(Exception("Door is already locked")))
            return@flow
        }

        emit(Result.success(LockState.Locking))
        repository.callService("lock", "lock", entityId).onFailure {
            emit(Result.failure(it))
            return@flow
        }

        val timeout = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < timeout) {
            delay(1500L)
            val stateResult = repository.getEntityState(entityId)
            if (stateResult.isSuccess) {
                val state = LockState.fromString(stateResult.getOrNull())
                emit(Result.success(state))
                if (state == LockState.Locked || state == LockState.Jammed || state == LockState.Unknown) {
                    return@flow
                }
            }
        }

        emit(Result.failure(Exception("Timeout waiting for door to lock")))
    }
}
