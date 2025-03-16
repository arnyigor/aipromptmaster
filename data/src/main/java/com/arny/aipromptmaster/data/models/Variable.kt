package com.arny.aipromptmaster.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Variable(
    @SerialName("name") val name: String,
    @SerialName("type") val type: String,
    @SerialName("description") val description: String
)