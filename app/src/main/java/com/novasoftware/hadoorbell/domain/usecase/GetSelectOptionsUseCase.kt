package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class GetSelectOptionsUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(entityId: String): Result<List<String>> {
        return repository.getSelectOptions(entityId)
    }
}
