package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.db.PromptEntity
import com.arny.aipromptmaster.domain.models.Prompt
import com.google.gson.Gson

object PromptMapper {
    // Преобразование из БД в домен
    fun mapFromEntity(entity: PromptEntity): Prompt {
        val tags = Gson().fromJson(entity.tagsJson, Array<String>::class.java).toList()
        return Prompt(
            id = entity.id,
            text = entity.text,
            model = entity.model,
            createdAt = entity.createdAt,
            tags = tags,
            rating = entity.rating
        )
    }

    // Преобразование из домена в БД
    fun mapToEntity(domain: Prompt): PromptEntity {
        val tagsJson = Gson().toJson(domain.tags)
        return PromptEntity(
            id = domain.id,
            text = domain.text,
            model = domain.model,
            createdAt = domain.createdAt,
            tagsJson = tagsJson,
            rating = domain.rating
        )
    }
}