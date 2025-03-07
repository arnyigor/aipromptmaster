package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.PromptDao
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import javax.inject.Inject

class PromptsRepositoryImpl @Inject constructor(
    private val dao: PromptDao
) : IPromptsRepository {
    override fun getAllPrompts(): Result<List<Prompt>> {
        return Result.success(dao.getAllPrompts())
    }
}