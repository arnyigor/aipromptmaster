package com.arny.aiprompts.data.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class ModelPricing(
    @Contextual val prompt: BigDecimal? = null,
    @Contextual val completion: BigDecimal? = null,
    @Contextual val image: BigDecimal? = null,
)
