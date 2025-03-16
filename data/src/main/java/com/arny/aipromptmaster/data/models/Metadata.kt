package com.arny.aipromptmaster.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Metadata(

    @SerialName("author") var author: Author? = Author(),
    @SerialName("source") var source: String? = null,
    @SerialName("notes") var notes: String? = null

)