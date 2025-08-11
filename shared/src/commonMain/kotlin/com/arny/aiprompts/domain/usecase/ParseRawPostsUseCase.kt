package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IFileParser
import com.arny.aiprompts.domain.model.RawPostData
import java.io.File

class ParseRawPostsUseCase(private val parser: IFileParser) {
    suspend operator fun invoke(file: File): Result<List<RawPostData>> {
        return runCatching {
            val htmlContent = file.readText()
            parser.parse(htmlContent)
        }
    }
}