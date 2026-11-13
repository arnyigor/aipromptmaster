package com.arny.aiprompts.data.llm

import com.arny.aiprompts.domain.interfaces.LLMService
import com.arny.aiprompts.domain.model.PostType

class NoOpLLMService : LLMService {
    override suspend fun generateTags(text: String): Result<List<String>> = Result.success(emptyList())
    override suspend fun classifyPost(textContent: String): Result<PostType> = Result.success(PostType.DISCUSSION)
}