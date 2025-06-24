package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName

data class ModelsResponseDTO(
    @SerializedName("data") val models: List<ModelDTO>
)
