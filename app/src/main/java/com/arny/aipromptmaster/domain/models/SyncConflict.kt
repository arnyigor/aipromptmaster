package com.arny.aipromptmaster.domain.models

sealed class SyncConflict {
    data class LocalNewer(val local: Prompt, val remote: Prompt) : SyncConflict()
    data class RemoteNewer(val local: Prompt, val remote: Prompt) : SyncConflict()
    data class ContentMismatch(val local: Prompt, val remote: Prompt) : SyncConflict()
} 