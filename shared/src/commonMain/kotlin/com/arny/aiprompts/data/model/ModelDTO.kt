package com.arny.aiprompts.data.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelDTO(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @Contextual @SerialName("context_length") val contextLength: Int,
    @SerialName("description") val description: String,
    @SerialName("created") val created: Long,
    @SerialName("architecture") val architecture: ModelArchitecture,
    @SerialName("pricing") val pricing: ModelPricing,
)

fun ModelDTO.toDomain(): LlmModel = LlmModel(
    id = id,
    name = name,
    description = description,
    created = created,
    contextLength = contextLength.toLong(),
    pricingPrompt = pricing.prompt.toBigDecimalOrNull(),
    pricingCompletion = pricing.completion.toBigDecimalOrNull(),
    pricingImage = pricing.image.toBigDecimalOrNull(),
    inputModalities = architecture.inputModalities,
    outputModalities = architecture.outputModalities,
    isSelected = false
)
