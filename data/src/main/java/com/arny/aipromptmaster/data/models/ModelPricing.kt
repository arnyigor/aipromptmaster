package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class ModelPricing(
    @SerializedName("prompt") val prompt: BigDecimal,
    @SerializedName("completion") val completion: BigDecimal,
    @SerializedName("image") val image: BigDecimal?,
)
