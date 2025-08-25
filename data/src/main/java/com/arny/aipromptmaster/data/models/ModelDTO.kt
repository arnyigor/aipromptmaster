package com.arny.aipromptmaster.data.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class ModelDTO(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @Contextual @SerialName("context_length") val contextLength: Int,
    @SerialName("description") val description: String,
    @SerialName("created") val created: Long,
    @SerialName("architecture") val architecture: ModelArchitectureDTO,
    @SerialName("pricing") val pricing: ModelPricingDTO,
)
