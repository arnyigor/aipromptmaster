package com.arny.aiprompts.domain.model

import kotlinx.serialization.Serializable

// Эта модель будет результатом работы парсера
@Serializable
data class PromptData(
    val id: String, // UUID
    val sourceId: String, // ID поста с форума
    val title: String,
    val description: String,
    val variants: List<PromptVariant>,
    val author: Author,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList(),
    val variables: List<String> = emptyList(),
    val category: String = "general",
    val isLocal: Boolean = true,
    val source: String = "4pda.to"
)

@Serializable
data class PromptVariant(
    val type: String = "prompt", // По умолчанию "prompt"
    val content: String
)