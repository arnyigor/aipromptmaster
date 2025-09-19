package com.arny.aiprompts.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelArchitecture(
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList(),
    @SerialName("tokenizer") val tokenizer: String? = null,
    @SerialName("instruct_type") val instructType: String? = null
)