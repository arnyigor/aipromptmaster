package com.arny.aiprompts.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromptJson(

    @SerialName("id") var id: String? = null,
    @SerialName("source_id") var sourceId: String? = null,
    @SerialName("title") var title: String? = null,
    @SerialName("version") var version: String? = null,
    @SerialName("status") var status: String? = null,
    @SerialName("is_local") var isLocal: Boolean = false,
    @SerialName("is_favorite") var isFavorite: Boolean = false,
    @SerialName("description") var description: String? = null,
    @SerialName("content") var content: Map<String, String> = emptyMap(),
    @SerialName("compatible_models") var compatibleModels: List<String> = emptyList(),
    @SerialName("category") var category: String? = null,
    @SerialName("tags") var tags: List<String> = emptyList(),
    @SerialName("variables") var variables: List<PromptVariable> = emptyList(),
    @SerialName("metadata") var metadata: PromptMetadata? = PromptMetadata(),
    @SerialName("rating") var rating: Rating? = Rating(),
    @SerialName("created_at") var createdAt: String? = null,
    @SerialName("updated_at") var updatedAt: String? = null

)