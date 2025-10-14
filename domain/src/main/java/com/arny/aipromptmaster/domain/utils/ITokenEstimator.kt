package com.arny.aipromptmaster.domain.utils

import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.TokenEstimationResult

interface ITokenEstimator {
    fun estimateTokens(
        inputText: String,
        attachedFiles: List<FileAttachment>,
        systemPrompt: String?,
        chatHistory: List<ChatMessage>,
    ): TokenEstimationResult

    fun adjustTokenRatio(actualPromptTokens: Int, estimatedPromptTokens: Int)
    fun resetRatio()
    fun getCurrentRatio(): Double
    fun isRatioAccurate(): Boolean
}