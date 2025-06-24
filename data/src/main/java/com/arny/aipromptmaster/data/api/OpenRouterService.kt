package com.arny.aipromptmaster.data.api

import com.arny.aipromptmaster.data.models.ChatCompletionRequestDTO
import com.arny.aipromptmaster.data.models.ChatCompletionResponseDTO
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenRouterService {

    @POST("chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String? = null,
        @Header("X-Title") title: String? = null,
        @Body request: ChatCompletionRequestDTO
    ): Response<ChatCompletionResponseDTO>
}
