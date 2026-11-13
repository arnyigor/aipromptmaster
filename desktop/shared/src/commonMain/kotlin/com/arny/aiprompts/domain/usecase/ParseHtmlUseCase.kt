package com.arny.aiprompts.domain.usecase


import com.arny.aiprompts.data.parser.IForumPromptParser
import com.arny.aiprompts.domain.model.PromptData
import java.io.File

class ParseHtmlUseCase(private val parser: IForumPromptParser) {
    suspend operator fun invoke(file: File): Result<List<PromptData>> {
        return runCatching {
            parser.parse(file.readText())
        }
    }
}