package com.novasoftware.hadoorbell.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

import com.google.gson.annotations.SerializedName

data class EntityStateResponse(
    @SerializedName("entity_id")
    val entityId: String,
    val state: String,
    val attributes: Map<String, Any>?
)

data class CallServiceRequest(
    @SerializedName("entity_id")
    val entityId: String
)

data class SetSelectOptionRequest(
    @SerializedName("entity_id")
    val entityId: String,
    val option: String
)

interface HomeAssistantApi {
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
