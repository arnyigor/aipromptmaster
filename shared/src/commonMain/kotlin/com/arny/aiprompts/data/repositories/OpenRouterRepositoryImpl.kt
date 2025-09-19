package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ChatCompletionRequest
import com.arny.aiprompts.data.model.ChatCompletionResponse
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.OpenRouterModelsResponse
import com.arny.aiprompts.data.model.ChatMessage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class OpenRouterRepositoryImpl(private val httpClient: HttpClient) : IOpenRouterRepository {

    private val _modelsFlow = MutableStateFlow<List<LlmModel>>(emptyList())

    override fun getModelsFlow(): Flow<List<LlmModel>> = _modelsFlow.asStateFlow()

    override suspend fun refreshModels(): Result<Unit> = try {
        println("Refreshing models...")
        val response: OpenRouterModelsResponse = httpClient.get("https://openrouter.ai/api/v1/models").body()
        _modelsFlow.value = response.data.map { dto -> dto.toDomain() }
        println("Models refreshed successfully.")
        Result.success(Unit)
    } catch (e: Exception) {
        println("Failed to refresh models. ${e.message}")
        e.printStackTrace()
        Result.failure(e)
    }

    override suspend fun getChatCompletion(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String
    ): Result<ChatCompletionResponse> = try {
        val response: ChatCompletionResponse = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(ChatCompletionRequest(model = model, messages = messages))
        }.body()

        // --- ГЛАВНОЕ ИСПРАВЛЕНИЕ ---
        // Проверяем, пришла ли в ответе ошибка
        if (response.error != null) {
            // Если да, возвращаем Result.failure с сообщением из API
            Result.failure(Exception("API Error: ${response.error.message}"))
        } else {
            // Если нет, возвращаем успешный результат
            Result.success(response)
        }

    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}