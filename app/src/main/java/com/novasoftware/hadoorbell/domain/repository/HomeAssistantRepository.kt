package com.novasoftware.hadoorbell.domain.repository

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import kotlinx.coroutines.flow.Flow

interface HomeAssistantRepository {
    suspend fun getEntities(url: String, token: String): Result<List<HomeAssistantEntity>>
    suspend fun getServices(url: String, token: String): Result<List<HomeAssistantService>>
    suspend fun getGo2rtcStreams(url: String, token: String): Result<List<String>>
    suspend fun getEntityState(entityId: String): Result<String>
    suspend fun getImage(path: String): Result<ByteArray>
    suspend fun getEntityAppearance(entityId: String): Result<HomeAssistantEntityAppearance>
    fun observeEntityAppearances(entityIds: Set<String>): Flow<Map<String, HomeAssistantEntityAppearance>>
    suspend fun callService(domain: String, service: String, entityId: String?): Result<Unit>
    suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        data: JsonObject,
        needsResponse: Boolean = false
    ): Result<Unit>
    suspend fun fireEvent(eventType: String, data: JsonObject): Result<Unit>
    suspend fun getSelectOptions(entityId: String): Result<List<String>>
    suspend fun setSelectOption(entityId: String, option: String): Result<Unit>
}
