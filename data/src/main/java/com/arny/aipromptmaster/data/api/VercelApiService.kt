package com.arny.aipromptmaster.data.api

import com.arny.aipromptmaster.data.models.PromptJson
import retrofit2.Response
import retrofit2.http.GET

interface VercelApiService {
    /**
     * Запрашивает все промпты из репозитория через прокси-эндпоинт на Vercel.
     * Возвращает готовый JSON-массив объектов промптов.
     */
    @GET("api/get-prompts")
    suspend fun getPrompts(): Response<List<PromptJson>>
}
