package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName

data class ModelArchitecture(
    @SerializedName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerializedName("output_modalities") val outputModalities: List<String> = emptyList()
)