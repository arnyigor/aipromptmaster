package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.daos.PromptHistoryDao
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.mappers.toEntity
import com.arny.aipromptmaster.domain.models.Pageable
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class PromptsRepositoryImpl @Inject constructor(
    private val promptDao: PromptDao,
    private val promptHistoryDao: PromptHistoryDao,
    private val dispatcher: CoroutineDispatcher
) : IPromptsRepository {

    override fun getAllPrompts(): Flow<List<Prompt>> = promptDao
        .getAllPrompts()
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

    override suspend fun cleanupHistory(olderThan: Date) = withContext(dispatcher) {
        promptHistoryDao.deleteHistoryBefore(olderThan)
    }

    override suspend fun savePrompts(prompts: List<Prompt>) = withContext(dispatcher) {
        prompts.forEach { prompt ->
            val entity = prompt.toEntity()
            promptDao.insertPrompt(entity)
        }
    }

    override fun getPromptsPaginated(
        search: String,
        category: String?,
        status: String?,
        tags: List<String>
    ): Pageable<Prompt> = object : Pageable<Prompt> {
        override suspend fun load(
            pageSize: Int,
            pageIndex: Int,
            onSuccess: (items: List<Prompt>, hasNextPage: Boolean) -> Unit,
            onError: (Throwable) -> Unit
        ) {
            try {
                val entities = promptDao.getPrompts(
                    search = search,
                    category = category,
                    status = status,
                    tags = tags.joinToString(","),
                    limit = pageSize,
                    offset = pageIndex * pageSize
                )
                val items = entities.map { it.toDomain() }
                onSuccess(items, items.size == pageSize)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    override fun getRecentChangesPaginated(changeType: String?): Pageable<Prompt> = object : Pageable<Prompt> {
        override suspend fun load(
            pageSize: Int,
            pageIndex: Int,
            onSuccess: (items: List<Prompt>, hasNextPage: Boolean) -> Unit,
            onError: (Throwable) -> Unit
        ) {
            try {
                val entities = promptHistoryDao.getRecentChanges(
                    changeType = changeType,
                    limit = pageSize,
                    offset = pageIndex * pageSize
                )
                val items = entities.map { it.toDomain() }
                onSuccess(items, items.size == pageSize)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    override fun getPromptHistoryPaginated(promptId: String): Pageable<Prompt> = object : Pageable<Prompt> {
        override suspend fun load(
            pageSize: Int,
            pageIndex: Int,
            onSuccess: (items: List<Prompt>, hasNextPage: Boolean) -> Unit,
            onError: (Throwable) -> Unit
        ) {
            try {
                val entities = promptHistoryDao.getPromptHistory(
                    promptId = promptId,
                    limit = pageSize,
                    offset = pageIndex * pageSize
                )
                val items = entities.map { it.toDomain() }
                onSuccess(items, items.size == pageSize)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}