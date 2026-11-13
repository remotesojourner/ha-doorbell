package com.novasoftware.hadoorbell.data.repository

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.data.remote.DraftHomeAssistantConnection
import com.novasoftware.hadoorbell.data.remote.EntityStateResponse
import com.novasoftware.hadoorbell.data.remote.HomeAssistantApi
import com.novasoftware.hadoorbell.data.remote.SetSelectOptionRequest
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.model.HomeAssistantServiceField
import com.novasoftware.hadoorbell.domain.model.normalizeHomeAssistantUrl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAssistantRepositoryImpl @Inject constructor(
    private val api: HomeAssistantApi
) : HomeAssistantRepository {
    override suspend fun getEntities(url: String, token: String): Result<List<HomeAssistantEntity>> =
        discover(url, token, "/api/states") { endpoint, authorization ->
            api.getEntities(endpoint, authorization, DraftHomeAssistantConnection).map { state ->
                HomeAssistantEntity(
                    entityId = state.entityId,
                    name = (state.attributes?.get("friendly_name") as? String)
                        ?.takeIf { it.isNotBlank() } ?: state.entityId,
                    options = (state.attributes?.get("options") as? List<*>).orEmpty()
                        .filterIsInstance<String>(),
                    icon = state.icon()
                )
            }
        }

    override suspend fun getServices(url: String, token: String): Result<List<HomeAssistantService>> =
        discover(url, token, "/api/services") { endpoint, authorization ->
            api.getServices(endpoint, authorization, DraftHomeAssistantConnection)
                .flatMap { domain ->
                    domain.services.entrySet().mapNotNull { (service, element) ->
                        val details = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        HomeAssistantService(
                            domain = domain.domain,
                            service = service,
                            name = details.text("name"),
                            description = details.text("description"),
                            fields = details.objectOrNull("fields")?.serviceFields().orEmpty(),
                            target = details.objectOrNull("target"),
                            needsResponse = details.objectOrNull("response")?.flag("optional") == false
                        )
                    }
                }
                .sortedWith(compareBy(HomeAssistantService::domain, HomeAssistantService::service))
        }

    private suspend fun <T> discover(
        url: String,
        token: String,
        path: String,
        request: suspend (HttpUrl, String) -> T
    ): Result<T> {
        val baseUrl = normalizeHomeAssistantUrl(url).toHttpUrlOrNull()
        if (baseUrl == null || !baseUrl.isHttps || baseUrl.username.isNotEmpty() || baseUrl.password.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a valid Home Assistant HTTPS URL"))
        }
        val accessToken = token.trim()
        if (accessToken.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a Home Assistant access token"))
        }
        val endpoint = baseUrl.newBuilder().encodedPath(path).query(null).fragment(null).build()
        return requestResult { request(endpoint, "Bearer $accessToken") }
    }

    override suspend fun getEntityState(entityId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getEntityState(entityId)
            Result.success(response.state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEntityIcon(entityId: String): Result<String?> =
        requestResult { api.getEntityState(entityId).icon() }

    override suspend fun callService(domain: String, service: String, entityId: String?): Result<Unit> =
        callService(domain, service, entityId, JsonObject())

    override suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        data: JsonObject,
        needsResponse: Boolean
    ): Result<Unit> = requestResult {
        val payload = data.deepCopy()
        if (!entityId.isNullOrBlank()) payload.addProperty("entity_id", entityId)
        api.callService(domain, service, payload, true.takeIf { needsResponse })
    }

    override suspend fun fireEvent(eventType: String, data: JsonObject): Result<Unit> =
        requestResult { api.fireEvent(eventType, data) }

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

    private fun JsonObject.serviceFields(): Map<String, HomeAssistantServiceField> = buildMap {
        for ((key, value) in entrySet()) {
            if (!value.isJsonObject) continue
            val details = value.asJsonObject
            val sectionFields = details.objectOrNull("fields")
            if (sectionFields != null) {
                putAll(sectionFields.serviceFields())
                continue
            }
            put(key, HomeAssistantServiceField(
                name = details.text("name"),
                description = details.text("description"),
                required = details.flag("required") == true,
                defaultValue = details.get("default"),
                selector = details.objectOrNull("selector")
            ))
        }
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.flag(key: String): Boolean? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

    private suspend fun <T> requestResult(request: suspend () -> T): Result<T> = try {
        Result.success(request())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
