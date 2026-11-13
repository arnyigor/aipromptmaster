package com.arny.aiprompts.domain.model

data class PromptContent(
    val ru: String = "",
    val en: String = ""
) {
    fun toMap(): Map<String, String> = mapOf(
        "ru" to ru,
        "en" to en
    )

    companion object {
        fun fromMap(map: Map<String, String>): PromptContent = PromptContent(
            ru = map["ru"] ?: "",
            en = map["en"] ?: ""
        )
    }
}