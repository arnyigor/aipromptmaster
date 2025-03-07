package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Prompt

interface IPromptsRepository {
    fun getAllPrompts(): Result<List<Prompt>>
}