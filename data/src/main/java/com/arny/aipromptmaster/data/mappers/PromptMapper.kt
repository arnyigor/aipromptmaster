package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.db.entities.PromptEntity
import com.arny.aipromptmaster.domain.models.Prompt

object PromptMapper {
    fun toEntity(domain: Prompt): PromptEntity = with(domain) {
        PromptEntity(
            id = id,
            title = title,
            description = description,
            template = template,
            variables = variables,
            aiModel = aiModel,
            category = category,
            language = language,
            tags = tags,
            isPrivate = isPrivate,
            rating = rating,
            successRate = successRate,
            status = status,
            settings = settings,
            authorId = authorId,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            parentId = parentId,
            version = version
        )
    }

    fun toDomain(entity: PromptEntity): Prompt = with(entity) {
        Prompt(
            id = id,
            title = title,
            description = description,
            template = template,
            variables = variables ?: emptyMap(),
            aiModel = aiModel,
            category = category,
            language = language,
            tags = tags,
            isPrivate = isPrivate,
            rating = rating,
            successRate = successRate,
            status = status,
            settings = settings ?: emptyMap(),
            authorId = authorId,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            parentId = parentId,
            version = version
        )
    }
}