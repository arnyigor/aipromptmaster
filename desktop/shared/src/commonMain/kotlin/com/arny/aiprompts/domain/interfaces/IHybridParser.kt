package com.arny.aiprompts.domain.interfaces

import com.arny.aiprompts.presentation.ui.importer.EditedPostData

interface IHybridParser {
    fun analyzeAndExtract(htmlContent: String): EditedPostData?
}