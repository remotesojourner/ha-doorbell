package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class GetHomeAssistantEntitiesUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(url: String, token: String): Result<List<HomeAssistantEntity>> =
        repository.getEntities(url, token).map { entities ->
            entities.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER, HomeAssistantEntity::name)
                    .thenBy(HomeAssistantEntity::entityId)
            )
        }
}
