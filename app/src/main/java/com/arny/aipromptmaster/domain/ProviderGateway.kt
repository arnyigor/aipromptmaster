package com.arny.aipromptmaster.domain

import com.arny.promptcontract.ProviderProfile
import kotlinx.coroutines.flow.Flow

data class ImprovementRequest(val source: String, val model: String, val instructions: String = "", val temperature: Double = 0.7, val maxTokens: Int = 1024, val stream: Boolean = true)
data class CompletionPiece(val content: String = "", val finishReason: String? = null, val complete: Boolean = false)
interface ProviderGateway : com.arny.promptcontract.ProviderProbe {
    fun improve(profile: ProviderProfile, request: ImprovementRequest): Flow<CompletionPiece>
}
