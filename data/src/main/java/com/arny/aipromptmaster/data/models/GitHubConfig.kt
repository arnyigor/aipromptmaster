package com.arny.aipromptmaster.data.models

data class GitHubConfig(
    val owner: String,
    val repo: String,
    val branch: String = "main",
    val promptsPath: String = "prompts"
) 