package com.arny.aiprompts.domain.model

data class ScrapedPost(
    val author: String,
    val text: String,
    val postId: String?
)