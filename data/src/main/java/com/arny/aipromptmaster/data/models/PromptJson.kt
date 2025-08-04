package com.arny.aipromptmaster.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromptJson(
    @SerialName("id") var id: String? = null,
    @SerialName("title") var title: String? = null,
    @SerialName("version") var version: String? = null,
    @SerialName("status") var status: String? = null,
    @SerialName("is_local") var isLocal: Boolean = false,
    @SerialName("is_favorite") var isFavorite: Boolean = false,
    @SerialName("description") var description: String? = null,
    @SerialName("content") var content: Map<String, String> = emptyMap(),
    @SerialName("prompt_variants") var promptVariants: List<PromptVariantJson> = emptyList(),
    @SerialName("compatible_models") var compatibleModels: List<String> = emptyList(),
    @SerialName("category") var category: String? = null,
    @SerialName("tags") var tags: List<String> = emptyList(),
    @SerialName("variables") var variables: List<VariableJson> = emptyList(),
    @SerialName("metadata") var metadata: MetadataJson? = MetadataJson(),
    @SerialName("rating") var rating: Rating? = Rating(),
    @SerialName("created_at") var createdAt: String? = null,
    @SerialName("updated_at") var updatedAt: String? = null
)

@Serializable
data class PromptVariantJson(
    @SerialName("variant_id") val variantId: VariantIdJson? = null,
    @SerialName("content") val content: Map<String, String>
)

@Serializable
data class VariantIdJson(
    @SerialName("type") val type: String,
    @SerialName("id") val id: String,
    @SerialName("priority") val priority: Int
)

@Serializable
data class VariableJson(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String?,
    @SerialName("default_value") val defaultValue: String? = null
)

@Serializable
data class MetadataJson(

    @SerialName("author") var author: AuthorJson? = AuthorJson(),
    @SerialName("source") var source: String? = null,
    @SerialName("notes") var notes: String? = null
)

@Serializable
data class AuthorJson(
    @SerialName("id") var id: String? = null,
    @SerialName("name") var name: String? = null
)

@Serializable
data class Rating(
    @SerialName("score") var score: Float = 0.0f,
    @SerialName("votes") var votes: Int = 0
)