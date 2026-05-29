package com.novasoftware.hadoorbell.integrations

import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HomeAssistantApiClient(
    private val haUrl: String,
    private val token: String
) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getSelectOptions(entityId: String): List<String> = suspendCancellableCoroutine { continuation ->
        val url = haUrl.trimEnd('/') + "/api/states/$entityId"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: "{}"
                    val map = gson.fromJson(bodyString, Map::class.java)
                    
                    val attributes = map["attributes"] as? Map<*, *>
                    @Suppress("UNCHECKED_CAST")
                    val options = attributes?.get("options") as? List<String> ?: emptyList()
                    
                    if (continuation.isActive) continuation.resume(options)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    suspend fun getEntityState(entityId: String): String = suspendCancellableCoroutine { continuation ->
        val url = haUrl.trimEnd('/') + "/api/states/$entityId"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: "{}"
                    val map = gson.fromJson(bodyString, Map::class.java)
                    
                    val state = map["state"] as? String ?: "unknown"
                    if (continuation.isActive) continuation.resume(state)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    suspend fun callService(domain: String, service: String, entityId: String): Unit = suspendCancellableCoroutine { continuation ->
        val url = haUrl.trimEnd('/') + "/api/services/$domain/$service"
        
        val payload = mapOf("entity_id" to entityId)
        val body = gson.toJson(payload).toRequestBody(jsonMediaType)
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    suspend fun setSelectOption(entityId: String, option: String): Unit = suspendCancellableCoroutine { continuation ->
        val domain = entityId.substringBefore(".", "select")
        val url = haUrl.trimEnd('/') + "/api/services/$domain/select_option"
        
        val payload = mapOf(
            "entity_id" to entityId,
            "option" to option
        )
        
        val body = gson.toJson(payload).toRequestBody(jsonMediaType)
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
