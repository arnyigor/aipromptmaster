package com.arny.aipromptmaster.domain.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.util.Date
import java.util.UUID

@OptIn(InternalSerializationApi::class)
@Serializable
data class Prompt(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String?,
    val content: PromptContent,
    val variables: Map<String, String> = emptyMap(),
    val promptVariants: List<DomainPromptVariant> = emptyList(),
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
    @Contextual val createdAt: Date = Date(),
    @Contextual val modifiedAt: Date = Date()
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class Author(
    val id: String = "",
    val name: String = ""
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class PromptMetadata(
    val author: Author = Author(),
    val source: String = "",
    val notes: String = ""
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class DomainPromptVariant(
    val variantId: DomainVariantId,
    val content: PromptContent
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class DomainVariantId(
    val type: String,
    val id: String,
    val priority: Int
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class PromptContent(
    val ru: String = "",
    val en: String = ""
) {
    fun toMap(): Map<String, String> = mapOf(
        "ru" to ru,
        "en" to en
    )

    companion object {
        fun fromMap(map: Map<String, String>): PromptContent = PromptContent(
            ru = map["ru"] ?: "",
            en = map["en"] ?: ""
        )
    }
}