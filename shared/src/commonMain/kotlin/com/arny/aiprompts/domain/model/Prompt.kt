package com.arny.aiprompts.domain.model

import com.arny.aiprompts.data.model.PromptMetadata
import kotlinx.datetime.Instant

data class Prompt(
    val id: String,
    val title: String,
    val description: String?,
    val content: PromptContent?,
    val variables: Map<String, String> = emptyMap(),
    val compatibleModels: List<String>,
    val category: String,
    val tags: List<String> = emptyList(),
    val isLocal: Boolean = true,
    val isFavorite: Boolean = false,
    val rating: Float = 0f,
    val ratingVotes: Int = 0,
    val status: String,
    val metadata: PromptMetadata = PromptMetadata(),
    val version: String = "1.0.0",
    val createdAt: Instant?,
    val modifiedAt: Instant?
)