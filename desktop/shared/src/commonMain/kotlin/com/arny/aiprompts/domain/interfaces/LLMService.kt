package com.arny.aiprompts.domain.interfaces

import com.arny.aiprompts.domain.model.PostType

interface LLMService {
    suspend fun generateTags(text: String): Result<List<String>>
    suspend fun classifyPost(textContent: String): Result<PostType>
}