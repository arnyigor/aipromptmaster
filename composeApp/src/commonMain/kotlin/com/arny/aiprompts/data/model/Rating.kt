package com.arny.aiprompts.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Rating(
    @SerialName("score") var score: Float = 0.0f,
    @SerialName("votes") var votes: Int = 0
)