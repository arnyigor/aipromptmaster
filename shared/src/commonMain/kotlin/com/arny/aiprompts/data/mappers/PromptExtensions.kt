package com.arny.aiprompts.data.mappers

import com.arny.aiprompts.data.db.entities.PromptEntity
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.data.model.PromptMetadata
import com.arny.aiprompts.data.model.Rating
import com.arny.aiprompts.domain.model.*
import com.benasher44.uuid.uuid4
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jsoup.Jsoup

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
    author = metadata.author?.name.orEmpty(),
    authorId = metadata.author?.id.orEmpty(),
    source = metadata.source.orEmpty(),
    notes = metadata.notes.orEmpty(),
    version = version,
    // Преобразуем Instant? в строку ISO 8601. Если null - пустая строка
    createdAt = this.createdAt?.toString().orEmpty(),
    modifiedAt = this.modifiedAt?.toString().orEmpty()
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
fun PromptJson.toDomain(): Prompt {
    fun parseDate(dateString: String?): Instant? {
        if (dateString.isNullOrBlank()) return null
        return try {
            // Сначала парсим как LocalDateTime, так как нет информации о зоне
            LocalDateTime.parse(dateString)
                // Затем считаем, что это время в UTC
                .toInstant(TimeZone.UTC)
        } catch (e: Exception) {
            // Если парсинг не удался, возвращаем null
            null
        }
    }
    return Prompt(
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
        createdAt = parseDate(createdAt),
        modifiedAt = parseDate(updatedAt)
    )
}

// Маппер из PromptData (результат парсинга) в PromptJson (DTO для файла)
fun PromptData.toPromptJson(): PromptJson {
    val createdAtString = Instant.fromEpochMilliseconds(this.createdAt).toString().removeSuffix("Z")
    val updatedAtString = Instant.fromEpochMilliseconds(this.updatedAt).toString().removeSuffix("Z")

    return PromptJson(
        id = this.id,
        sourceId = this.sourceId, // Добавляем связь с постом
        title = this.title,
        version = "1.0.0",
        status = "active",
        isLocal = false, // Импортированные промпты будут сразу отправляться в git
        isFavorite = false,
        description = this.description,
        content = mapOf("ru" to (this.variants.firstOrNull()?.content ?: "")),
        compatibleModels = emptyList(), // Пока пустой список
        category = this.category,
        tags = this.tags,
        variables = emptyList(), // Пока пустой список
        metadata = PromptMetadata(
            author = Author(id = this.author.id, name = this.author.name),
            source = this.source,
            notes = "Импортировано из поста ${this.sourceId}"
        ),
        rating = Rating(), // Рейтинг по умолчанию
        createdAt = createdAtString,
        updatedAt = updatedAtString
    )
}

// Маппер из "сырого" поста в "финальный" PromptData.
// Здесь происходит финальная бизнес-логика и очистка.
fun RawPostData.toPromptData(): PromptData {
    // Используем Jsoup для финальной очистки HTML в plain text
    val cleanContent = Jsoup.parse(this.fullHtmlContent).text()

    return PromptData(
        id = uuid4().toString(),
        sourceId = this.postId,
        title = "Prompt for ${this.author.name} (${this.postId})", // TODO: Улучшить извлечение заголовка
        description = cleanContent.take(200), // Берем первые 200 символов как описание
        variants = listOf(PromptVariant(content = cleanContent)),
        author = this.author,
        createdAt = this.date.toEpochMilliseconds(),
        updatedAt = this.date.toEpochMilliseconds(),
        category = "imported"
    )
}