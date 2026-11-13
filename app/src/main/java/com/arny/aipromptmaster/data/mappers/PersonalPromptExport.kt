@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.promptcontract.*
import kotlin.time.Instant

fun Prompt.toExportJson(): PromptJson = (wireDocument ?: PromptJson()).copy(
    id = id, title = title, description = description,
    content = (wireDocument?.content.orEmpty() + mapOf("ru" to content.ru, "en" to content.en)),
    category = category, status = status, tags = tags, compatibleModels = compatibleModels.map(String::trim).filter(String::isNotBlank),
    isLocal = isLocal, isFavorite = isFavorite,
    variables = wireDocument?.variables ?: variables.map { (name, value) -> VariableJson(name, defaultValue = value) },
    promptVariants = wireDocument?.promptVariants?.takeIf { variants -> variants.map { it.toDomain() } == promptVariants }
        ?: promptVariants.map { PromptVariantJson(VariantIdJson(it.variantId.type, it.variantId.id, it.variantId.priority), mapOf("ru" to it.content.ru, "en" to it.content.en), it.variantId.priority) },
    metadata = MetadataJson(AuthorJson(metadata.author.id, metadata.author.name), metadata.source, metadata.notes),
    rating = Rating(rating, ratingVotes), version = version,
    createdAt = Instant.fromEpochMilliseconds(createdAt.time).toString(), updatedAt = Instant.fromEpochMilliseconds(modifiedAt.time).toString(),
)
