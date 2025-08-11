package com.arny.aiprompts.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GitHubCommitResponse(val sha: String)