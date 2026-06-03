package com.novasoftware.hadoorbell.data.repository

import com.novasoftware.hadoorbell.data.remote.CallServiceRequest
import com.novasoftware.hadoorbell.data.remote.HomeAssistantApi
import com.novasoftware.hadoorbell.data.remote.SetSelectOptionRequest
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAssistantRepositoryImpl @Inject constructor(
    private val api: HomeAssistantApi
) : HomeAssistantRepository {
    override suspend fun getEntityState(entityId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getEntityState(entityId)
            Result.success(response.state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun callService(domain: String, service: String, entityId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            api.callService(domain, service, CallServiceRequest(entityId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSelectOptions(entityId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getEntityState(entityId)
            @Suppress("UNCHECKED_CAST")
            val options = response.attributes?.get("options") as? List<String> ?: emptyList()
            Result.success(options)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setSelectOption(entityId: String, option: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val domain = entityId.substringBefore(".", "select")
            api.setSelectOption(domain, SetSelectOptionRequest(entityId, option))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
