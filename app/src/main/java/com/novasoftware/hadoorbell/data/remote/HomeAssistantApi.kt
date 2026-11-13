package com.novasoftware.hadoorbell.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Tag
import retrofit2.http.Url

import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl

data class EntityStateResponse(
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("state")
    val state: String,
    @SerializedName("attributes")
    val attributes: Map<String, Any>?
)

data class CallServiceRequest(
    @SerializedName("entity_id")
    val entityId: String?
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

    @GET("api/states/{entity_id}")
    suspend fun getEntityState(
        @Path("entity_id") entityId: String
    ): EntityStateResponse

    @POST("api/services/{domain}/{service}")
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body request: CallServiceRequest
    )

    @POST("api/services/{domain}/select_option")
    suspend fun setSelectOption(
        @Path("domain") domain: String,
        @Body request: SetSelectOptionRequest
    )
}
