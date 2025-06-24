package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.Message
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.results.LLMResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LLMInteractor @Inject constructor(
    private val repository: IOpenRouterRepository
) : ILLMInteractor {

    override suspend fun sendMessage(
        model: String,
        userMessage: String
    ): Flow<LLMResult<String>> = flow {
        emit(LLMResult.Loading())

        try {
            val messages = listOf(
                Message("user", userMessage)
            )

            val result = repository.getChatCompletion(model, messages)

            result.fold(
                onSuccess = { response ->
                    val content = response.choices.firstOrNull()?.message?.content
                    if (content != null) {
                        emit(LLMResult.Success(content))
                    } else {
                        emit(LLMResult.Error(Exception("Empty response")))
                    }
                },
                onFailure = { exception ->
                    emit(LLMResult.Error(exception))
                }
            )
        } catch (e: Exception) {
            emit(LLMResult.Error(e))
        }
    }
}