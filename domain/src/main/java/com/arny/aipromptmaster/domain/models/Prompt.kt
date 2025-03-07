package com.arny.aipromptmaster.domain.models

data class Prompt(
    val id: Int,
    val text: String,
    val model: String,
    val createdAt: Long,
    val tags: List<String> = emptyList(),
    val rating: Float? = null
)