package com.arny.aipromptmaster.domain.models

/**
 * Базовый класс для всех ожидаемых бизнес-ошибок (ошибок доменного слоя).
 * В отличие от технических исключений (IOException, SQLiteException), эти ошибки
 * представляют собой нарушения бизнес-правил, которые UI должен уметь обрабатывать.
 */
sealed class DomainException(override val message: String? = null) : Exception(message) {
    data object NoModelSelected : DomainException() {
        private fun readResolve(): Any = NoModelSelected
    }

    data object ModelListUnavailable : DomainException() {
        private fun readResolve(): Any = ModelListUnavailable
    }
}