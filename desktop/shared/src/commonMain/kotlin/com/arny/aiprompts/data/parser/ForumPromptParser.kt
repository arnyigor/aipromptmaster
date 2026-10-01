package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.PromptData

interface IForumPromptParser {
    suspend fun parse(htmlContent: String): List<PromptData>
}