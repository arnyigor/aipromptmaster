package com.arny.aiprompts.data.model

/**
 * Список исключений, связанных с API‑запросами OpenRouter.
 *
 * Включает отсутствие ключа, HTTP‑ошибки и ошибки парсинга.
 */
sealed class ApiException : Exception() {

    /**
     * Исключение, генерируемое, если в настройках не найден API‑ключ.
     */
    class MissingApiKey : ApiException() {
        override val message: String =
            "OpenRouter API ключ не настроен. Пожалуйста, введите API ключ в настройках."
    }

    /**
     * Исключение для HTTP‑ошибок сервера OpenRouter.
     *
     * @property code   Код ответа (например, 401, 500 и т.д.).
     * @property body   Текст тела ошибки.
     */
    data class HttpError(val code: Int, val body: String) : ApiException() {
        override val message: String = "API Error ($code): $body"
    }

    /**
     * Исключение, возникающее при ошибке парсинга JSON‑ответа.
     *
     * @property raw Текст, который не удалось распарсить.
     */
    data class ParseError(val raw: String) : ApiException() {
        override val message: String = "Failed to parse response: $raw"
    }
}