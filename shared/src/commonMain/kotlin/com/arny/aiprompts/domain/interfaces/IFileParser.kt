package com.arny.aiprompts.domain.interfaces

import com.arny.aiprompts.domain.model.RawPostData

// Интерфейс в commonMain
interface IFileParser {
    fun parse(htmlContent: String): List<RawPostData>
}