package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.models.ModelDTO
import com.arny.aipromptmaster.domain.models.LlmModel

fun ModelDTO.toDomain(): LlmModel {
    return LlmModel(
        id = id,
        name = name,
        description = description,
        isSelected = false
    )
}