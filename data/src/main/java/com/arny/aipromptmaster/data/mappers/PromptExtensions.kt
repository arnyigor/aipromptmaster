package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.db.entities.PromptEntity
import com.arny.aipromptmaster.data.models.PromptJson
import com.arny.aipromptmaster.data.models.PromptVariantJson
import com.arny.aipromptmaster.data.utils.toIsoDate
import com.arny.aipromptmaster.data.utils.toIsoString
import com.arny.aipromptmaster.domain.models.Author
import com.arny.aipromptmaster.domain.models.DomainPromptVariant
import com.arny.aipromptmaster.domain.models.DomainVariantId
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.PromptMetadata
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import java.util.UUID

private val jsonParser = Json { ignoreUnknownKeys = true }

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
    createdAt = createdAt.toIsoString(),
    promptVariantsJson = jsonParser.encodeToString(promptVariants),
    modifiedAt = modifiedAt.toIsoString(),
)

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
    promptVariants = jsonParser.decodeFromString(promptVariantsJson),
    createdAt = createdAt.toIsoDate(),
    modifiedAt = modifiedAt.toIsoDate(),
)

fun PromptJson.toDomain(): Prompt {
    val variablesMap = variables.associate { it.name to it.defaultValue.orEmpty() }

    val domainVariants = promptVariants.map { it.toDomain() }

    return Prompt(
        id = id ?: UUID.randomUUID().toString(),
        title = title.orEmpty(),
        description = description,
        content = PromptContent.fromMap(content),
        variables = variablesMap,
        promptVariants = domainVariants, // Добавляем варианты
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
                id = metadata?.author?.id.orEmpty(),
                name = metadata?.author?.name.orEmpty()
            ),
            source = metadata?.source.orEmpty(),
            notes = metadata?.notes.orEmpty()
        ),
        version = version.orEmpty(),
        createdAt = createdAt.orEmpty().toIsoDate(),
        modifiedAt = updatedAt.orEmpty().toIsoDate()
    )
}

fun PromptVariantJson.toDomain(): DomainPromptVariant = DomainPromptVariant(
    variantId = DomainVariantId(
        type = this.variantId?.type.orEmpty(),
        id = this.variantId?.id.orEmpty(),
        priority = this.variantId?.priority ?: 0
    ),
    content = PromptContent.fromMap(this.content)
)
