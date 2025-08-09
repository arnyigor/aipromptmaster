package com.arny.aiprompts.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelDTO(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("context_length") val contextLength: Long? = null,
    @SerialName("created") val createdAt: Long? = null, // Даты часто приходят как строки
    val architecture: ModelArchitecture? = null,
    val pricing: ModelPricing? = null,
)

fun ModelDTO.toDomain(): LlmModel = LlmModel(
    id = id,
    name = name.orEmpty(),
    description = description.orEmpty(),
    isSelected = false,
    contextLength = contextLength,
    created = createdAt ?: 0L,
    inputModalities = architecture?.inputModalities.orEmpty(),
    outputModalities = architecture?.outputModalities.orEmpty(),
    pricingPrompt = pricing?.prompt,
    pricingCompletion = pricing?.completion,
    pricingImage = pricing?.image
)
