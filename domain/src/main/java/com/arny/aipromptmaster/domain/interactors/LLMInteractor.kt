package com.arny.aipromptmaster.domain.interactors

// LLMInteractor.kt
import android.util.Log
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.Message
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class LLMInteractor @Inject constructor(
    private val modelsRepository: IOpenRouterRepository,
    private val settingsRepository: ISettingsRepository,
    private val historyRepository: IChatHistoryRepository
) : ILLMInteractor {

    override fun sendMessage(model: String, userMessage: String): Flow<DataResult<String>> =
        flow {
            emit(DataResult.Loading)
            try {
                // 1. Получаем текущую историю чата. `first()` берет последнее значение из Flow.
                val currentHistory = historyRepository.getHistoryFlow().first()

                // 2. Создаем сообщение пользователя и сразу добавляем его в историю.
                historyRepository.addMessages(listOf(Message(role = "user", content = userMessage)))

                // 3. Формируем полный контекст для API.
                // Берем историю (которая уже включает новое сообщение) и передаем в репозиторий.
                val messagesForApi = historyRepository.getHistoryFlow().first()

                // 4. Проверяем API ключ
                val apiKey = settingsRepository.getApiKey()?.trim()
                if (apiKey.isNullOrEmpty()) {
                    emit(DataResult.Error(IllegalArgumentException("API key is required")))
                    return@flow
                }

                // 5. Выполняем запрос с полным контекстом
                val result = modelsRepository.getChatCompletion(model, messagesForApi, apiKey)

                result.fold(
                    onSuccess = { response ->
                        val content = response.choices.firstOrNull()?.message?.content
                        if (content != null) {
                            // 6. При успехе добавляем ответ модели в историю
                            val modelMessage = Message(role = "assistant", content = content)
                            historyRepository.addMessages(listOf(modelMessage))
                            emit(DataResult.Success(content))
                        } else {
                            emit(DataResult.Error(Exception("Empty response from API")))
                        }
                    },
                    onFailure = { exception -> emit(DataResult.Error(exception)) }
                )
            } catch (e: Exception) {
                emit(DataResult.Error(e))
            }
        }

    // НОВЫЙ МЕТОД для получения истории для UI
    override fun getChatHistoryFlow(): Flow<List<Message>> = historyRepository.getHistoryFlow()

    // НОВЫЙ МЕТОД для очистки истории
    override suspend fun clearChat() {
        historyRepository.clearHistory()
    }

    /**
     * Предоставляет поток со списком моделей, обогащенным состоянием выбора.
     */
    override fun getModels(): Flow<DataResult<List<LlmModel>>> {
        val selectedIdFlow: Flow<String?> = settingsRepository.getSelectedModelId()
        val modelsListFlow: Flow<List<LlmModel>> = modelsRepository.getModelsFlow()
        return combine(selectedIdFlow, modelsListFlow) { selectedId, modelsList ->
            Log.i(this::class.java.simpleName, "getModels: selectedId: $selectedId, modelsList: ${modelsList.size}")
            // Эта лямбда будет выполняться каждый раз, когда меняется ID или список моделей.
            if (modelsList.isEmpty()) {
                DataResult.Loading
            } else {
                val mappedList = modelsList.map { model ->
                    model.copy(isSelected = model.id == selectedId)
                }
                DataResult.Success(mappedList)
            }
        }.onStart { emit(DataResult.Loading) } // Начинаем с Loading в любом случае.
    }

    /**
     * Возвращает реактивный поток с деталями только одной выбранной модели.
     */
    override fun getSelectedModel(): Flow<DataResult<LlmModel>> {
        return getModels().map { dataResult ->
            when (dataResult) {
                is DataResult.Success -> {
                    val selected = dataResult.data.find { it.isSelected }
                    if (selected != null) {
                        DataResult.Success(selected)
                    } else {
                        DataResult.Error(null, R.string.selected_model_not_found)
                    }
                }

                is DataResult.Error -> DataResult.Error(dataResult.exception)
                is DataResult.Loading -> dataResult
            }
        }
    }


    /**
     * Сохраняет выбор пользователя в репозитории настроек.
     */
    override suspend fun selectModel(id: String) {
        settingsRepository.setSelectedModelId(id)
    }

    /**
     * Запускает принудительное обновление списка моделей.
     */
    override suspend fun refreshModels(): Result<Unit> = modelsRepository.refreshModels()

    /**
     * НОВЫЙ МЕТОД: Обрабатывает клик, решая, выбрать или отменить выбор.
     */
    override suspend fun toggleModelSelection(clickedModelId: String) {
        // 1. Получаем ТЕКУЩИЙ выбранный ID.
        //    Используем `first()` чтобы получить однократное значение из потока.
        val currentlySelectedId = settingsRepository.getSelectedModelId().firstOrNull()

        // 2. Принимаем решение
        if (currentlySelectedId == clickedModelId) {
            // Если кликнули на уже выбранную модель -> отменяем выбор
            settingsRepository.setSelectedModelId(null)
        } else {
            // Если кликнули на другую модель -> выбираем ее
            settingsRepository.setSelectedModelId(clickedModelId)
        }
    }
}
