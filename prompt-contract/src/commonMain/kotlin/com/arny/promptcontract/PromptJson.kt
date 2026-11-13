@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.arny.promptcontract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/** Shared wire format; storage and presentation remain platform adapters. */
@Serializable
data class PromptJson(
    var id: String? = null,
    @SerialName("source_id") var sourceId: String? = null,
    var title: String? = null,
    var version: String? = null,
    var status: String? = null,
    @SerialName("is_local") @JsonNames("isLocal") var isLocal: Boolean = false,
    @SerialName("is_favorite") @JsonNames("isFavorite") var isFavorite: Boolean = false,
    var description: String? = null,
    var content: Map<String, String> = emptyMap(),
    @SerialName("prompt_variants") var promptVariants: List<PromptVariantJson> = emptyList(),
    @SerialName("compatible_models") @JsonNames("compatibleModels") var compatibleModels: List<String> = emptyList(),
    var category: String? = null,
    var tags: List<String> = emptyList(),
    var variables: List<VariableJson> = emptyList(),
    var metadata: MetadataJson? = MetadataJson(),
    var rating: Rating? = Rating(),
    @SerialName("created_at") @JsonNames("createdAt") var createdAt: String? = null,
    @SerialName("updated_at") @JsonNames("modifiedAt", "updatedAt") var updatedAt: String? = null
)

@Serializable
data class PromptVariantJson(
    @SerialName("variant_id") @JsonNames("variantId") val variantId: VariantIdJson? = null,
    val content: Map<String, String> = emptyMap(),
    val priority: Int? = null
)

@Serializable
data class VariantIdJson(val type: String = "prompt", val id: String = "", val priority: Int = 1)

@Serializable
data class VariableJson(
    val name: String,
    val description: String? = null,
    @SerialName("default_value") val defaultValue: String? = null,
    val type: String = "string",
    val examples: List<String> = emptyList()
)

@Serializable
data class MetadataJson(
    var author: AuthorJson? = AuthorJson(),
    var source: String? = null,
    var notes: String? = null
)

@Serializable
data class AuthorJson(var id: String? = null, var name: String? = null)

@Serializable
data class Rating(var score: Float = 0f, var votes: Int = 0)
