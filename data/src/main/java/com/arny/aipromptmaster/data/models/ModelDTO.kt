package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class ModelDTO(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("context_length") val contextLength: BigDecimal,
    @SerializedName("description") val description: String,
    @SerializedName("created") val created: Long,
    @SerializedName("architecture") val architecture: ModelArchitecture,
    @SerializedName("pricing") val pricing: ModelPricing,
)
