package com.arny.aipromptmaster.domain.models.errors

/**
 * Запечатанный класс для представления всех возможных ошибок,
 * с которыми может столкнуться UI.
 */
sealed class DomainError(message: String?) : Exception(message) {

    /**
     * Ошибка, полученная от API, с детальной информацией.
     * @param code HTTP-код или кастомный код ошибки.
     * @param userFriendlyMessage Краткое сообщение для заголовка или Toast.
     * @param detailedMessage Полное сообщение от API (бывший 'raw') для диалога.
     */
    data class Api(
        val code: Int,
        val userFriendlyMessage: String,
        val detailedMessage: String
    ) : DomainError(userFriendlyMessage)

    /**
     * Ошибка сети (например, таймаут, отсутствие подключения).
     */
    data class Network(override val message: String) : DomainError(message)

    /**
     * Локальная ошибка, возникшая на устройстве (например, невалидные данные).
     */
    data class Local(override val message: String) : DomainError(message)

    /**
     * Общая ошибка, например, отсутствие сети или неизвестное исключение.
     */
    data class Generic(override val message: String?) : DomainError(message)
}

fun Throwable.getFriendlyMessage(): String {
    return when (this) {
        is DomainError.Api -> this.userFriendlyMessage
        is DomainError.Network -> this.message
        is DomainError.Local -> this.message
        is DomainError.Generic -> this.message ?: "Произошла неизвестная ошибка"
        else -> this.message ?: "Неизвестная ошибка"
    }
}
