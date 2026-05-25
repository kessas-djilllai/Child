package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class Message(
    val id: String,
    val message: String? = null,
    val text: String? = null,
    val content: String? = null,
    @Json(name = "created_at") val createdAt: String
) {
    fun getDisplayText(): String {
        return message ?: text ?: content ?: "New Activity"
    }
}

interface SupabaseApi {
    @GET("rest/v1/messages")
    suspend fun getMessages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 1
    ): List<Message>
}
