package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.models.ModelDTO
import com.arny.aipromptmaster.domain.models.LLMModel

fun ModelDTO.toDomain(): LLMModel {
    return LLMModel(
        id = id,
        name = name,
        description = description,
        contextLength = contextLength,
    )
}