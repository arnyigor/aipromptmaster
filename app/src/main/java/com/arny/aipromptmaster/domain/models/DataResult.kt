package com.arny.aipromptmaster.domain.models

import com.arny.aipromptmaster.domain.models.errors.DomainError

sealed class DataResult<out T> {
    data object Loading : DataResult<Nothing>()
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error<T>(val error: DomainError) : DataResult<T>()
}
