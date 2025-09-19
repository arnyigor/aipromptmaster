package com.arny.aiprompts.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelPricing(
    @SerialName("prompt") val prompt: String = "0", // Оставляем String, конвертируем в mapper'е
    @SerialName("completion") val completion: String = "0",
    @SerialName("image") val image: String = "0",
    @SerialName("request") val request: String = "0",
    @SerialName("web_search") val webSearch: String = "0",
    @SerialName("internal_reasoning") val internalReasoning: String = "0",
    @SerialName("input_cache_read") val inputCacheRead: String = "0",
    @SerialName("input_cache_write") val inputCacheWrite: String = "0"
)
