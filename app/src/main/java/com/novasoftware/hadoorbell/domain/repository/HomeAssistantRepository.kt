package com.novasoftware.hadoorbell.domain.repository

import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity

interface HomeAssistantRepository {
    suspend fun getEntities(url: String, token: String): Result<List<HomeAssistantEntity>>
    suspend fun getEntityState(entityId: String): Result<String>
    suspend fun callService(domain: String, service: String, entityId: String?): Result<Unit>
    suspend fun getSelectOptions(entityId: String): Result<List<String>>
    suspend fun setSelectOption(entityId: String, option: String): Result<Unit>
}
