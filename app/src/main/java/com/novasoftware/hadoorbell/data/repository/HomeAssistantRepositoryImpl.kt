package com.novasoftware.hadoorbell.data.repository

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.data.remote.EntityStateResponse
import com.novasoftware.hadoorbell.data.remote.ExplicitHomeAssistantConnection
import com.novasoftware.hadoorbell.data.remote.FRIGATE_INSTANCE_ID
import com.novasoftware.hadoorbell.data.remote.HomeAssistantApi
import com.novasoftware.hadoorbell.data.remote.HomeAssistantStateClient
import com.novasoftware.hadoorbell.data.remote.SetSelectOptionRequest
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.model.HomeAssistantServiceField
import com.novasoftware.hadoorbell.domain.model.normalizeHomeAssistantUrl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAssistantRepositoryImpl @Inject constructor(
    private val api: HomeAssistantApi,
    private val settings: SettingsRepository,
    private val stateClient: HomeAssistantStateClient
) : HomeAssistantRepository {
    override suspend fun getEntities(url: String, token: String): Result<List<HomeAssistantEntity>> =
        discover(url, token, "/api/states") { endpoint, authorization ->
            api.getEntities(endpoint, authorization, ExplicitHomeAssistantConnection).map { state ->
                HomeAssistantEntity(
                    entityId = state.entityId,
                    name = (state.attributes?.get("friendly_name") as? String)
                        ?.takeIf { it.isNotBlank() } ?: state.entityId,
                    options = (state.attributes?.get("options") as? List<*>).orEmpty()
                        .filterIsInstance<String>(),
                    icon = state.attribute("icon"),
                    state = state.state,
                    deviceClass = state.attribute("device_class")
                )
            }
        }

    override suspend fun getServices(url: String, token: String): Result<List<HomeAssistantService>> =
        discover(url, token, "/api/services") { endpoint, authorization ->
            api.getServices(endpoint, authorization, ExplicitHomeAssistantConnection)
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

    override suspend fun getGo2rtcStreams(url: String, token: String): Result<List<String>> =
        discover(url, token, "/api/frigate/$FRIGATE_INSTANCE_ID/go2rtc/api/streams") { endpoint, authorization ->
            api.getJsonObject(endpoint, authorization, ExplicitHomeAssistantConnection)
                .keySet()
                .filter(String::isNotBlank)
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

    private suspend fun <T> discover(
        url: String,
        token: String,
        path: String,
        request: suspend (HttpUrl, String) -> T
    ): Result<T> = requestResult {
        request(homeAssistantEndpoint(url, path), "Bearer ${accessToken(token)}")
    }

    private fun homeAssistantEndpoint(url: String, path: String): HttpUrl {
        val baseUrl = normalizeHomeAssistantUrl(url).toHttpUrlOrNull()
        require(baseUrl != null && baseUrl.isHttps && baseUrl.username.isEmpty() && baseUrl.password.isEmpty()) {
            "Enter a valid Home Assistant HTTPS URL"
        }
        return baseUrl.newBuilder().encodedPath(path).query(null).fragment(null).build()
    }

    private fun accessToken(token: String): String = token.trim().also {
        require(it.isNotEmpty()) { "Enter a Home Assistant access token" }
    }

    override suspend fun getImage(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        requestResult {
            require(path.startsWith("/") && !path.startsWith("//")) { "Images must use a Home Assistant path" }
            api.download(path).use { it.bytes() }
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

    override suspend fun getEntityAppearance(entityId: String): Result<HomeAssistantEntityAppearance> =
        requestResult {
            val entity = api.getEntityState(entityId)
            HomeAssistantEntityAppearance(entity.state, entity.attribute("icon"), entity.attribute("device_class"))
        }

    override fun observeEntityAppearances(
        entityIds: Set<String>
    ): Flow<Map<String, HomeAssistantEntityAppearance>> {
        if (entityIds.isEmpty()) return flowOf(emptyMap())
        return flow {
            val (url, token) = settings.homeAssistantConnectionFlow.first()
            val appearances = try {
                stateClient.observe(homeAssistantEndpoint(url, "/api/websocket"), accessToken(token), entityIds)
            } catch (error: IllegalArgumentException) {
                throw IOException("Invalid Home Assistant entity subscription connection", error)
            }
            emitAll(appearances)
        }
    }

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

    private fun EntityStateResponse.attribute(key: String): String? =
        (attributes?.get(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

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
