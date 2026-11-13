package com.novasoftware.hadoorbell.data.remote

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag
import retrofit2.http.Url

data class EntityStateResponse(
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("state")
    val state: String,
    @SerializedName("attributes")
    val attributes: Map<String, Any>?
)

data class ServiceDomainResponse(
    @SerializedName("domain")
    val domain: String,
    @SerializedName("services")
    val services: JsonObject
)

data class SetSelectOptionRequest(
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("option")
    val option: String
)

/** Marks a discovery request that must use the supplied URL and authorization header. */
object DraftHomeAssistantConnection

interface HomeAssistantApi {
    @GET
    suspend fun getEntities(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String,
        @Tag connection: DraftHomeAssistantConnection
    ): List<EntityStateResponse>

    @GET
    suspend fun getServices(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String,
        @Tag connection: DraftHomeAssistantConnection
    ): List<ServiceDomainResponse>

    @GET("api/states/{entity_id}")
    suspend fun getEntityState(
        @Path("entity_id") entityId: String
    ): EntityStateResponse

    @POST("api/services/{domain}/{service}")
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body request: JsonObject,
        @Query("return_response") returnResponse: Boolean? = null
    )

    @POST("api/events/{event_type}")
    suspend fun fireEvent(
        @Path("event_type") eventType: String,
        @Body data: JsonObject
    )

    @POST("api/services/{domain}/select_option")
    suspend fun setSelectOption(
        @Path("domain") domain: String,
        @Body request: SetSelectOptionRequest
    )
}
