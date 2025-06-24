package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName

data class ModelDTO(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("context_length") val contextLength: Int,
    @SerializedName("description") val description: String,
)
