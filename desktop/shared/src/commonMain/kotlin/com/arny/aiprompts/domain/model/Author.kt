package com.arny.aiprompts.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Author(
    val id: String = "",
    val name: String = ""
)