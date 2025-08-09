package com.arny.aiprompts.data.model

import com.arny.aiprompts.domain.model.Author
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromptMetadata(
    @SerialName("author") var author: Author? = Author(),
    @SerialName("source") var source: String? = null,
    @SerialName("notes") var notes: String? = null
)