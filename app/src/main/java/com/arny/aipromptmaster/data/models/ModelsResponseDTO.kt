package com.arny.aipromptmaster.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelsResponseDTO(
    @SerialName("data") val models: List<ModelDTO>
)
