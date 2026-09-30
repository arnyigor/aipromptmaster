package com.arny.aipromptmaster.domain

import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import kotlinx.coroutines.flow.*

class ImprovePromptUseCase(private val gateway: ProviderGateway, private val settings: ISettingsRepository, private val models: com.arny.aipromptmaster.domain.repositories.ModelRepository? = null) {
    suspend fun selectedModel(): String = settings.loadProviders().active.modelId.ifBlank { models?.getSelectedModel()?.id.orEmpty() }
    operator fun invoke(request: ImprovementRequest): Flow<String> = flow {
        require(request.source.isNotBlank()) { "Введите исходный промпт" }
        require(request.model.isNotBlank()) { "Выберите модель в профиле провайдера или введите ID" }
        require(request.temperature in 0.0..2.0 && request.maxTokens in 1..16384) { "Проверьте температуру и лимит токенов" }
        val profile = settings.loadProviders().active.validated()
        val buffer = StringBuilder()
        var finished = false
        fun visible(): String = buffer.toString().replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").substringBefore("<think>").trim()
        gateway.improve(profile, request).collect { piece ->
            require(piece.finishReason != "length") { "Ответ обрезан. Увеличьте лимит токенов." }
            finished = finished || piece.complete
            buffer.append(piece.content)
            emit(visible())
        }
        require(finished && visible().isNotBlank()) { "Ответ пустой или поток оборвался. Повторите запрос." }
    }
}
