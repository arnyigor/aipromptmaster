package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.SyncConflict

sealed class SyncResult {
    data class Success(val updatedPrompts: List<Prompt>) : SyncResult()
    data class Error(val message: String) : SyncResult()
    data class Conflicts(val conflicts: List<SyncConflict>) : SyncResult()
}