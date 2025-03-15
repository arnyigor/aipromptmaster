package com.arny.aipromptmaster.data.models

data class PromptJson(
    val id: String,
    val title: String,
    val version: String,
    val status: String,
    val isLocal: Boolean,
    val isFavorite: Boolean,
    val description: String?,
    val content: Map<String, String>,
    val compatibleModels: List<String>,
    val category: String,
    val tags: List<String>,
    val variables: List<Variable>,
    val metadata: Map<String, Any>,
    val rating: Rating,
    val createdAt: String,
    val updatedAt: String
)