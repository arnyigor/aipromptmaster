package com.arny.aiprompts.domain.repositories

import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.strings.StringHolder

sealed class SyncResult {
    data class Success(val prompts: List<Prompt>) : SyncResult()
    data class Error(val message: StringHolder) : SyncResult()
    data object TooSoon : SyncResult()
}