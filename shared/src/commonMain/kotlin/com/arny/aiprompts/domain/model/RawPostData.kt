package com.arny.aiprompts.domain.model

import kotlinx.datetime.Instant

/**
 * Data-класс для хранения "сырых", минимально обработанных данных,
 * извлеченных из одного HTML-элемента поста.
 * Эта структура используется для передачи данных в UI для верификации.
 */
data class RawPostData(
    val postId: String,
    val author: Author,
    val date: Instant,
    val fullHtmlContent: String,    // Весь внутренний HTML <div class="postcolor">
    val isLikelyPrompt: Boolean,    // Флаг-подсказка от "детектора"
    val fileAttachmentUrl: String? = null // URL на .txt вложение, если есть
)