package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.repositories.ILLmRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LLMInteractor @Inject constructor(
    private val repository: ILLmRepository,
    private val settingsRepository: ISettingsRepository
) : ILLMInteractor {

    override suspend fun sendMessage(
        model: String,
        userMessage: String
    ): Flow<DataResult<String>> = flow {
        emit(DataResult.Loading)
        try {
            val messages = listOf(
                ChatMessage("user", userMessage)
            )
            val apiKey = settingsRepository.getApiKey()
            if (apiKey.isNullOrEmpty()) {
                throw IllegalArgumentException("API key is required")
            }
    
            val result = repository.getChatCompletion(model, messages, apiKey)

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
        emit(DataResult.Loading)
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