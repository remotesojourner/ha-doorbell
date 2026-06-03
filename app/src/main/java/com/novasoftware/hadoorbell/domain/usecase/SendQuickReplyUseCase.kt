package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class SendQuickReplyUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(entityId: String, option: String): Result<Unit> {
        return repository.setSelectOption(entityId, option)
    }
}
