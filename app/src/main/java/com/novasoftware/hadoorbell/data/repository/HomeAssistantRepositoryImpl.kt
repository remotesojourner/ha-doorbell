package com.novasoftware.hadoorbell.data.repository

import com.novasoftware.hadoorbell.data.remote.CallServiceRequest
import com.novasoftware.hadoorbell.data.remote.DraftHomeAssistantConnection
import com.novasoftware.hadoorbell.data.remote.EntityStateResponse
import com.novasoftware.hadoorbell.data.remote.HomeAssistantApi
import com.novasoftware.hadoorbell.data.remote.SetSelectOptionRequest
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.normalizeHomeAssistantUrl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAssistantRepositoryImpl @Inject constructor(
    private val api: HomeAssistantApi
) : HomeAssistantRepository {
    override suspend fun getEntities(url: String, token: String): Result<List<HomeAssistantEntity>> {
        val baseUrl = normalizeHomeAssistantUrl(url).toHttpUrlOrNull()
        if (baseUrl == null || !baseUrl.isHttps || baseUrl.username.isNotEmpty() || baseUrl.password.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a valid Home Assistant HTTPS URL"))
        }
        val accessToken = token.trim()
        if (accessToken.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a Home Assistant access token"))
        }
        val statesUrl = baseUrl.newBuilder()
            .encodedPath("/api/states")
            .query(null)
            .fragment(null)
            .build()

        return try {
            val states = api.getEntities(statesUrl, "Bearer $accessToken", DraftHomeAssistantConnection)
            Result.success(states.map { state ->
                HomeAssistantEntity(
                    entityId = state.entityId,
                    name = (state.attributes?.get("friendly_name") as? String)
                        ?.takeIf { it.isNotBlank() } ?: state.entityId,
                    options = (state.attributes?.get("options") as? List<*>).orEmpty()
                        .filterIsInstance<String>(),
                    icon = state.icon()
                )
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEntityState(entityId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getEntityState(entityId)
            Result.success(response.state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEntityIcon(entityId: String): Result<String?> = try {
        Result.success(api.getEntityState(entityId).icon())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun callService(domain: String, service: String, entityId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            api.callService(domain, service, CallServiceRequest(entityId))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun EntityStateResponse.icon(): String? =
        (attributes?.get("icon") as? String)?.trim()?.takeIf { it.isNotEmpty() }
}
