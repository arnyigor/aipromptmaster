package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.errors.DomainError
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * UseCase для получения списка промптов с применением фильтров.
 */
class GetPromptsUseCase(
    private val promptsRepository: IPromptsRepository
) {
    /**
     * Возвращает Flow, который эмитирует либо список промптов, либо ошибку.
     * Это более реактивный подход, чем suspend функция с Result.
     */
    fun getPromptsFlow(): Flow<Result<List<Prompt>>> {
        // Мы будем вызывать метод getPrompts из репозитория,
        // но для реактивности, представим что у нас есть flow-версия
        // Для MVP начнем с getAllPromptsFlow
        return promptsRepository.getAllPrompts()
            // .map { ... здесь можно применить фильтрацию на стороне клиента, если нужно }
            .map { prompts -> Result.success(prompts) }
            .catch { throwable ->
                // Если в Flow происходит ошибка, ловим ее
                // и преобразуем в наш DomainError
                val error = when (throwable) {
                    is DomainError -> throwable // Если это уже наша ошибка, пробрасываем ее
                    else -> DomainError.Local("An unexpected error occurred in the data flow.")
                }
                emit(Result.failure(error))
            }
    }

    /**
     * Метод для одноразового поискового запроса.
     * Идеально подходит для пагинации при нажатии "Загрузить еще".
     */
    suspend fun search(
        query: String,
        category: String? = null,
        status: String? = null,
        tags: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 20
    ): Result<List<Prompt>> {
        val result = runCatching {
            promptsRepository.getPrompts(
                search = query,
                category = category,
                status = status,
                tags = tags,
                offset = offset,
                limit = limit
            )
        }
        return if (result.isSuccess) {
            result
        } else {
            val error = when (val throwable = result.exceptionOrNull()!!) {
                is DomainError -> throwable
                else -> DomainError.Local("Failed to perform search.")
            }
            Result.failure(error)
        }
    }
}