package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.models.ChatCompletionResponseDTO
import com.arny.aipromptmaster.data.models.ChoiceDTO
import com.arny.aipromptmaster.data.models.MessageDTO
import com.arny.aipromptmaster.data.models.UsageDTO
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.Choice
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.Usage

object ChatMapper {
    fun toDomain(dto: ChatCompletionResponseDTO): ChatCompletionResponse {
        return ChatCompletionResponse(
            id = dto.id,
            choices = dto.choices.map { toDomainChoice(it) },
            usage = dto.usage?.let { toDomainUsage(it) }
        )
    }

    private fun toDomainChoice(dto: ChoiceDTO): Choice {
        return Choice(
            message = toDomainMessage(dto.message),
            finishReason = dto.finishReason
        )
    }

    private fun toDomainMessage(dto: MessageDTO): ChatMessage {
        return ChatMessage(
            role = dto.role,
            content = dto.content
        )
    }

    private fun toDomainUsage(dto: UsageDTO): Usage {
        return Usage(
            promptTokens = dto.promptTokens,
            completionTokens = dto.completionTokens,
            totalTokens = dto.totalTokens
        )
    }
}