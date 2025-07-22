package com.arny.aipromptmaster.data.api

import com.arny.aipromptmaster.data.models.FeedbackRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface FeedbackApiService {
    @POST("api/feedback") // Указываем относительный путь
    @Headers("Content-Type: application/json")
    suspend fun sendFeedback(@Body request: FeedbackRequestDto): Response<Unit>
}