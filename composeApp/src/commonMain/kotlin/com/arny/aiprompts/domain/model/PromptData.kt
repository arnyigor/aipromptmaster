package com.arny.aiprompts.domain.model

import kotlinx.serialization.Serializable

// Эта модель будет результатом работы парсера
@Serializable
data class PromptData(
    val id: String, // ID поста
    val title: String,
    val description: String,
    val variants: List<PromptVariant>,
    val author: Author,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList(), // Могут быть добавлены позже
    val category: String = "general", // Категория по умолчанию
    val source: String = "4pda.to" // Источник по умолчанию
)

@Serializable
data class PromptVariant(
    val type: String = "prompt", // По умолчанию "prompt"
    val content: String
)