package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.models.ModelDTO
import com.arny.aipromptmaster.domain.models.LlmModel

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