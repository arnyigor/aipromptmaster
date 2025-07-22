package com.arny.aipromptmaster.domain.repositories

interface IFeedbackRepository {
    suspend fun sendFeedback(content: String): Result<Unit>
}