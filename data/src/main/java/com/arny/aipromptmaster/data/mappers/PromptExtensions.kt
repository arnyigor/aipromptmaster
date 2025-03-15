package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.db.entities.PromptEntity
import com.arny.aipromptmaster.data.db.entities.PromptHistoryEntity
import com.arny.aipromptmaster.data.models.PromptJson
import com.arny.aipromptmaster.data.models.Rating
import com.arny.aipromptmaster.data.models.Variable
import com.arny.aipromptmaster.data.utils.toDate
import com.arny.aipromptmaster.data.utils.toIsoString
import com.arny.aipromptmaster.domain.models.Author
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.PromptMetadata

// Domain -> Entity
fun Prompt.toEntity(): PromptEntity = PromptEntity(
    id = id,
    title = title,
    contentRu = content.ru,
    contentEn = content.en,
    description = description,
    category = category,
    status = status,
    tags = tags.joinToString(","),
    isLocal = isLocal,
    isFavorite = isFavorite,
    rating = rating,
    ratingVotes = ratingVotes,
    compatibleModels = compatibleModels.joinToString(),
    author = metadata.author.name,
    authorId = metadata.author.id,
    source = metadata.source,
    notes = metadata.notes,
    version = version,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

// Entity -> Domain
fun PromptEntity.toDomain(): Prompt = Prompt(
    id = id,
    title = title,
    content = PromptContent(
        ru = contentRu,
        en = contentEn
    ),
    description = description,
    category = category,
    status = status,
    tags = tags.split(",").filter { it.isNotBlank() },
    isLocal = isLocal,
    isFavorite = isFavorite,
    rating = rating,
    ratingVotes = ratingVotes,
    compatibleModels = compatibleModels.split(","),
    metadata = PromptMetadata(
        author = Author(
            id = authorId,
            name = author
        ),
        source = source,
        notes = notes
    ),
    version = version,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

// API -> Domain
fun PromptJson.toDomain(): Prompt = Prompt(
    id = id,
    title = title,
    description = description,
    content = PromptContent(
        ru = content["ru"].orEmpty(),
        en = content["en"].orEmpty()
    ),
    variables = variables.associate { it.name to it.defaultValue },
    compatibleModels = compatibleModels,
    category = category.lowercase(),
    tags = tags,
    isLocal = isLocal,
    isFavorite = isFavorite,
    rating = rating.score,
    ratingVotes = rating.votes,
    status = status.lowercase(),
    metadata = PromptMetadata(
        author = Author(
            id = metadata["authorId"].toString(),
            name = metadata["author"].toString()
        ),
        source = metadata["source"].toString(),
        notes = metadata["notes"].toString()
    ),
    version = version,
    createdAt = createdAt.toDate(),
    modifiedAt = updatedAt.toDate()
)

// Domain -> History
fun Prompt.toHistory(changeType: String): PromptHistoryEntity = PromptHistoryEntity(
    promptId = id,
    title = title,
    contentRu = content.ru,
    contentEn = content.en,
    description = description,
    category = category,
    status = status,
    tags = tags.joinToString(","),
    isLocal = isLocal,
    isFavorite = isFavorite,
    rating = rating,
    ratingVotes = ratingVotes,
    compatibleModels = compatibleModels.joinToString(),
    author = metadata.author.name,
    authorId = metadata.author.id,
    source = metadata.source,
    notes = metadata.notes,
    version = version,
    changeType = changeType,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

// History -> Domain
fun PromptHistoryEntity.toDomain(): Prompt = Prompt(
    id = promptId,
    title = title,
    content = PromptContent(
        ru = contentRu,
        en = contentEn
    ),
    description = description,
    category = category,
    status = status,
    tags = tags.split(",").filter { it.isNotBlank() },
    isLocal = isLocal,
    isFavorite = isFavorite,
    rating = rating,
    ratingVotes = ratingVotes,
    compatibleModels = compatibleModels.split(","),
    metadata = PromptMetadata(
        author = Author(
            id = authorId,
            name = author
        ),
        source = source,
        notes = notes
    ),
    version = version,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

// Domain -> JSON
fun Prompt.toJson(): PromptJson = PromptJson(
    id = id,
    title = title,
    description = description,
    content = mapOf(
        "ru" to content.ru,
        "en" to content.en
    ),
    variables = variables.map { (name, defaultValue) -> 
        Variable(name, defaultValue)
    },
    compatibleModels = compatibleModels,
    category = category,
    tags = tags,
    isLocal = isLocal,
    isFavorite = isFavorite,
    rating = Rating(rating, ratingVotes),
    status = status,
    metadata = mapOf(
        "authorId" to metadata.author.id,
        "author" to metadata.author.name,
        "source" to metadata.source,
        "notes" to metadata.notes
    ),
    version = version,
    createdAt = createdAt.toIsoString(),
    updatedAt = modifiedAt.toIsoString()
) 