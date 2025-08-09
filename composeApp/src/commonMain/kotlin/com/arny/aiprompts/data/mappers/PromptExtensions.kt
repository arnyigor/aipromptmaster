package com.arny.aiprompts.data.mappers

import com.arny.aiprompts.data.db.entities.PromptEntity
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.model.PromptContent
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.domain.model.PromptMetadata
import com.benasher44.uuid.uuid4 // Кросс-платформенный генератор UUID
import kotlinx.datetime.Instant

// Domain -> Entity
fun Prompt.toEntity(): PromptEntity = PromptEntity(
    id = id,
    title = title,
    contentRu = content?.ru.orEmpty(),
    contentEn = content?.en.orEmpty(),
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
    // Преобразуем Instant? в строку ISO 8601. Если null - пустая строка
    createdAt = this.createdAt?.toString() ?: "",
    modifiedAt = this.modifiedAt?.toString() ?: ""
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
    tags = if (tags.isNotBlank()) tags.split(",") else emptyList(),
    isLocal = isLocal,
    isFavorite = isFavorite,
    rating = rating,
    ratingVotes = ratingVotes,
    compatibleModels = if (compatibleModels.isNotBlank()) compatibleModels.split(",") else emptyList(),
    metadata = PromptMetadata(
        author = Author(id = authorId, name = author),
        source = source,
        notes = notes
    ),
    version = version,
    // Безопасно парсим строку в Instant?
    createdAt = if (createdAt.isNotBlank()) Instant.parse(createdAt) else null,
    modifiedAt = if (modifiedAt.isNotBlank()) Instant.parse(modifiedAt) else null,
)

// API -> Domain
fun PromptJson.toDomain(): Prompt = Prompt(
    // Если id нет в JSON, генерируем новый кросс-платформенный UUID
    id = id ?: uuid4().toString(),
    title = title.orEmpty(),
    description = description,
    content = PromptContent(
        ru = content["ru"].orEmpty(),
        en = content["en"].orEmpty()
    ),
    variables = variables.associate { it.name to it.type },
    compatibleModels = compatibleModels,
    category = category.orEmpty().lowercase(),
    tags = tags,
    isLocal = isLocal,
    isFavorite = isFavorite,
    rating = rating?.score ?: 0.0f,
    ratingVotes = rating?.votes ?: 0,
    status = status.orEmpty().lowercase(),
    metadata = PromptMetadata(
        author = Author(
            id = metadata?.author?.name.orEmpty(),
            name = metadata?.author?.name.orEmpty()
        ),
        source = metadata?.source.orEmpty(),
        notes = metadata?.notes.orEmpty()
    ),
    version = version.orEmpty(),
    // Безопасно парсим строку из JSON в Instant?
    createdAt = createdAt?.let { Instant.parse(it) },
    modifiedAt = updatedAt?.let { Instant.parse(it) }
)