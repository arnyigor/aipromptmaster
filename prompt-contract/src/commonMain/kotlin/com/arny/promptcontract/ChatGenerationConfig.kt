package com.arny.promptcontract

import kotlinx.serialization.Serializable

@Serializable
data class ChatGenerationConfig(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 0.9f,
    val contextWindow: Int = 10,
) {
    fun checked(): ChatGenerationConfig {
        require(temperature.isFinite() && temperature in 0f..2f)
        require(maxTokens in 1..8192)
        require(topP.isFinite() && topP in 0f..1f)
        require(contextWindow in 1..50)
        return this
    }
}
