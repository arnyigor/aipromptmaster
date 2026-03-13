package com.arny.aiprompts.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Represents a variant of a prompt.
 * Each prompt can have multiple variants (e.g., different languages, versions, etc.)
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PromptVariant @OptIn(ExperimentalSerializationApi::class) constructor(
    val variantId: VariantId = VariantId(),
    val content: PromptContentMap = PromptContentMap(),
    @EncodeDefault
    val priority: Int = 1
)

/**
 * Unique identifier for a prompt variant.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VariantId @OptIn(ExperimentalSerializationApi::class) constructor(
    val type: String = "prompt",
    val id: String = ""
)

/**
 * Content map for prompt variant (supports multiple languages).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PromptContentMap @OptIn(ExperimentalSerializationApi::class) constructor(
    val ru: String = "",
    val en: String = ""
)
