package com.arny.aipromptmaster.domain.models

import java.util.Date
import java.util.UUID

data class Prompt(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String?,
    val template: String,
    val variables: Map<String, String> = emptyMap(),
    val aiModel: String,
    val category: PromptCategory,
    val language: String,
    val tags: List<String> = emptyList(),
    val isPrivate: Boolean,
    val rating: Float = 0f,
    val successRate: Float? = null,
    val status: PromptStatus,
    val settings: Map<String, Any> = emptyMap(),
    val authorId: String?,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val parentId: String?,
    val version: Int = 1
)