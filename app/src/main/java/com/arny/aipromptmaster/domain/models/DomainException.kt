package com.arny.aipromptmaster.domain.models

/**
 * Базовый класс для всех ожидаемых бизнес-ошибок (ошибок доменного слоя).
 * В отличие от технических исключений (IOException, SQLiteException), эти ошибки
 * представляют собой нарушения бизнес-правил, которые UI должен уметь обрабатывать.
 */
sealed class DomainException(override val message: String? = null) : Exception(message) {
    class NoModelSelected : DomainException() {
        private fun readResolve(): Any = NoModelSelected()
    }

    class ModelListUnavailable : DomainException() {
        private fun readResolve(): Any = ModelListUnavailable()
    }

    data class ContextLimitExceeded(val required: Boolean, val limit: Int) : DomainException()
    data class FileContentMissing(val fileName: String) : DomainException()
}