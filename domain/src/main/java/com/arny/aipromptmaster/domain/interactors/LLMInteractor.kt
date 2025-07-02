package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.AppConstants.ERROR_MODEL_NOT_FOUND
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.models.Message
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LLMInteractor @Inject constructor(
    private val repository: IOpenRouterRepository,
    private val settingsRepository: ISettingsRepository
) : ILLMInteractor {

    override suspend fun sendMessage(
        model: String,
        userMessage: String
    ): Flow<DataResult<String>> = flow {
        emit(DataResult.Loading)
        try {
            val messages = listOf(
                Message("user", userMessage)
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

    override suspend fun getSelectedModel(): Flow<DataResult<LLMModel>> = flow {
        try {
            // 1. Сначала получаем ID. Это быстрая операция.
            val modelId = repository.getSelectedModelId()

            // 2. Используем "guard clause" (ранний выход). Если ID нет,
            // сразу же эмитим ошибку и прекращаем выполнение.
            if (modelId == null) {
                emit(DataResult.Error(exception = Throwable(ERROR_MODEL_NOT_FOUND)))
                return@flow // Важно: завершаем работу flow-билдера
            }

            // 3. Только если ID существует, загружаем полный список моделей.
            // Предположим, что getModels() возвращает Result<List<LLMModel>>
            val modelsResult = repository.getModels()

            modelsResult.fold(
                onSuccess = { llmModels ->
                    if (llmModels.isEmpty()) {
                        emit(DataResult.Error(
                            exception = null,
                            messageRes = R.string.empty_models_list
                        ))
                        return@fold
                    }

                    // 4. Ищем модель и используем оператор Elvis для обработки случая, когда модель не найдена
                    val foundModel = llmModels.find { it.id == modelId }

                    if (foundModel != null) {
                        emit(DataResult.Success(foundModel))
                    } else {
                        emit(DataResult.Error(
                            exception = null,
                            messageRes = R.string.selected_model_not_found
                        ))
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

    override suspend fun setSelectedModelId(id: String) {
        repository.setSelectedModelId(id)
    }
}