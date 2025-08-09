package com.arny.aiprompts.data.repository

import com.arny.aiprompts.data.db.daos.PromptDao
import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.data.mappers.toEntity
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PromptsRepositoryImpl(
    private val promptDao: PromptDao,
    private val dispatcher: CoroutineDispatcher
) : IPromptsRepository {

    override suspend fun getPromptsCount(): Int = withContext(dispatcher) {
        promptDao.getPromptsCount()
    }

    override fun getAllPrompts(): Flow<List<Prompt>> = promptDao
        .getAllPromptsFlow()
        .map { entities -> entities.map { it.toDomain() } }

    override suspend fun getPromptById(promptId: String): Prompt? = withContext(dispatcher) {
        promptDao.getById(promptId)?.toDomain()
    }

    override suspend fun insertPrompt(prompt: Prompt): Long = withContext(dispatcher) {
        val entity = prompt.toEntity()
        promptDao.insertPrompt(entity)
    }

    override suspend fun updatePrompt(prompt: Prompt) = withContext(dispatcher) {
        val entity = prompt.toEntity()
        promptDao.updatePrompt(entity)
    }

    override suspend fun deletePrompt(promptId: String) = withContext(dispatcher) {
        promptDao.delete(promptId)
    }

    override suspend fun deletePromptsByIds(promptIds: List<String>) = withContext(dispatcher) {
        promptDao.deletePromptsByIds(promptIds)
    }

    override suspend fun toggleFavoriteStatus(promptId: String) = withContext(dispatcher) {
        promptDao.toggleFavoriteStatus(promptId)
    }

    override suspend fun savePrompts(prompts: List<Prompt>) = withContext(dispatcher) {
        // 1. Получаем все локальные промпты из базы ОДНИМ запросом.
        val localPrompts = promptDao.getAllPrompts().associateBy { it.id }

        // 2. Создаем "слитый" список.
        val mergedPrompts = prompts.map { remotePrompt ->
            // Ищем соответствующий локальный промпт.
            val localPrompt = localPrompts[remotePrompt.id]

            // Если локальный промпт существует и он избранный,
            // то мы создаем копию удаленного промпта, но с флагом isFavorite = true.
            if (localPrompt != null && localPrompt.isFavorite) {
                remotePrompt.copy(isFavorite = true)
            } else {
                // Иначе просто берем промпт с сервера как есть.
                remotePrompt
            }
        }

        // 3. Сохраняем "слитый" список в базу.
        // OnConflictStrategy.REPLACE теперь работает правильно: он заменяет данные,
        // но флаг isFavorite мы уже сохранили.
        val entitiesToSave = mergedPrompts.map { it.toEntity() }
        entitiesToSave.forEach { entity ->
            promptDao.insertPrompt(entity)
        }
    }

    override suspend fun getPrompts(
        search: String,
        category: String?,
        status: String?,
        tags: List<String>,
        offset: Int,
        limit: Int
    ): List<Prompt> = withContext(dispatcher) {

        val prompts = if (tags.isEmpty()) {
            // Если фильтра по тегам нет, используем простой и быстрый запрос
            promptDao.getPromptsWithoutTags(
                searchQuery = search,
                category = category,
                status = status,
                limit = limit,
                offset = offset
            )
        } else {
            // Если есть теги, строим строку с условиями
            // Пример: " (',' || tags || ',' LIKE '%,general,%') AND (',' || tags || ',' LIKE '%,ai,%') "
            val tagCondition = tags.joinToString(separator = " AND ") { tag ->
                // Оборачиваем в запятые, чтобы избежать частичных совпадений (например, "ai" в "train")
                " (',' || tags || ',' LIKE '%,' || :tag_${tag} || ',%') "
            }

            // К сожалению, Room не позволяет напрямую подставлять такую строку в @Query.
            // Это ограничение.
            // Значит, нам придется фильтровать теги уже после получения данных из БД.

            // ПОЭТОМУ, САМЫЙ РЕАЛИСТИЧНЫЙ ВАРИАНТ БЕЗ ИЗМЕНЕНИЯ СХЕМЫ:

            // 1. Получаем из БД все, что подходит под другие фильтры
            val allMatchingPrompts = promptDao.getPromptsWithoutTags(
                searchQuery = search,
                category = category,
                status = status,
                limit = Int.MAX_VALUE, // Загружаем все, чтобы отфильтровать в памяти
                offset = 0
            )

            // 2. Фильтруем по тегам в коде
            val filteredByTags = allMatchingPrompts.filter { entity ->
                val entityTags = entity.tags.split(',').map { it.trim() }.toSet()
                // Проверяем, что все теги из фильтра содержатся в тегах сущности
                tags.all { filterTag -> entityTags.contains(filterTag) }
            }

            // 3. Применяем пагинацию (limit/offset) вручную
            filteredByTags.drop(offset).take(limit)
        }

        prompts.map { it.toDomain() }
    }
}