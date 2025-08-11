package com.arny.aiprompts.data.llm

import com.arny.aiprompts.domain.interfaces.LLMService
import com.arny.aiprompts.domain.model.PostType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class OllamaLLMService(private val client: HttpClient) : LLMService {
    // ...
    // Здесь будет реализация запросов к Ollama
    override suspend fun generateTags(text: String): Result<List<String>> { TODO() }
    override suspend fun classifyPost(textContent: String): Result<PostType> { TODO() }
}