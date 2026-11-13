@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.arny.aiprompts.data.mappers

import com.arny.aiprompts.domain.model.Prompt
import com.arny.promptcontract.AuthorJson
import com.arny.promptcontract.MetadataJson
import com.arny.promptcontract.PromptJson
import com.arny.promptcontract.Rating
import com.arny.promptcontract.VariableJson

fun Prompt.toExportJson(): PromptJson = (wireDocument ?: PromptJson()).copy(
    id = id, title = title, description = description,
    content = wireDocument?.content.orEmpty() + mapOf("ru" to content?.ru.orEmpty(), "en" to content?.en.orEmpty()),
    category = category, status = status, tags = tags, compatibleModels = compatibleModels.map(String::trim).filter(String::isNotBlank),
    isLocal = isLocal, isFavorite = isFavorite,
    variables = wireDocument?.variables ?: variables.map { (name, value) -> VariableJson(name, defaultValue = value) },
    metadata = MetadataJson(AuthorJson(metadata.author?.id, metadata.author?.name), metadata.source, metadata.notes),
    rating = Rating(rating, ratingVotes), version = version,
    createdAt = createdAt?.toString(), updatedAt = modifiedAt?.toString()
)
