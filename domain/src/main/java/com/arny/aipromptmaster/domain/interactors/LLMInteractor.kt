package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.models.Message
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LLMInteractor @Inject constructor(
    private val repository: IOpenRouterRepository
) : ILLMInteractor {

    override suspend fun sendMessage(
        model: String,
        userMessage: String
    ): Flow<DataResult<String>> = flow {
        emit(DataResult.Loading())
        try {
            val messages = listOf(
                Message("user", userMessage)
            )

            val result = repository.getChatCompletion(model, messages)

            result.fold(
                onSuccess = { response ->
                    val content = response.choices.firstOrNull()?.message?.content
                    if (content != null) {
                        emit(DataResult.Success(content))
                    } else {
                        emit(DataResult.Error(Exception("Empty response")))
                    }
                },
                onFailure = { exception ->
                    emit(DataResult.Error(exception))
                }
            )
        } catch (e: Exception) {
            emit(DataResult.Error(e))
        }
    }


    override suspend fun getModels(): Flow<DataResult<List<LLMModel>>> = flow {
        emit(DataResult.Loading())
        try {
            val result = repository.getModels() // пока что берем всегда кешированные модели

            result.fold(
                onSuccess = { models ->
                    emit(DataResult.Success(models))
                },
                onFailure = { exception ->
                    emit(DataResult.Error(exception))
                }
            )
        } catch (e: Exception) {
            emit(DataResult.Error(e))
        }
    }
}