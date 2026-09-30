package com.arny.aiprompts.domain.model

enum class PostType {
    STANDARD_PROMPT,
    META_PROMPT,
    JAILBREAK,
    TEMPLATE_PROMPT,
    FILE_ATTACHMENT,
    EXTERNAL_RESOURCE,
    DISCUSSION, // Тип по умолчанию для постов, которые не удалось классифицировать
    UNKNOWN // Для постов, которые еще не были обработаны
}