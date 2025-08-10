package com.arny.aiprompts.domain.interfaces

import com.arny.aiprompts.presentation.ui.importer.ExtractedPromptData

// Интерфейс, который ничего не знает о Jsoup
interface IHybridParser {
    fun analyzeAndExtract(htmlContent: String): ExtractedPromptData?
}