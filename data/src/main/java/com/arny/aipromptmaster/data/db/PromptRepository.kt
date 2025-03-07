package com.arny.aipromptmaster.data.db


class PromptRepository(
    private val promptDao: PromptDao,
    private val tagDao: TagDao,
    private val promptTagDao: PromptTagDao
) :IPromptRepository{

    // Пример сохранения промпта с тегами
    suspend fun savePromptWithTags(prompt: PromptEntity, tagEntities: List<TagEntity>) {
        val promptId = promptDao.insert(prompt).toInt()
        tagEntities.forEach { tag ->
            val tagId = tagDao.insert(tag).toInt()
            promptTagDao.insertCrossRef(PromptTagCrossRef(promptId, tagId))
        }
    }

    // Получение промпта с тегами
    fun observePromptsWithTags() = promptDao.getPromptsWithTags()
}