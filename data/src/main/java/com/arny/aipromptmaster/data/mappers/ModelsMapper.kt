package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.models.ApiError
import com.arny.aipromptmaster.data.models.ModelDTO
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError


fun ModelDTO.toDomain(): LlmModel = LlmModel(
    id = id,
    name = name,
    description = description,
    isSelected = false,
    contextLength = contextLength,
    created = created,
    inputModalities = architecture.inputModalities,
    outputModalities = architecture.outputModalities,
    pricingPrompt = pricing.prompt,
    pricingCompletion = pricing.completion,
    pricingImage = pricing.image
)

// Функция-расширение для чистой конвертации
fun ApiError.toDomainError(): DomainError.Api {
    return DomainError.Api(
        code = this.code,
        userFriendlyMessage = "Ошибка API (Код: ${this.code})",
        detailedMessage = this.metadata?.raw ?: this.message
    )
}