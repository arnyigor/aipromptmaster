package com.arny.aipromptmaster.domain.results

sealed class DataResult<out T> {
    object Loading : DataResult<Nothing>()
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error<T>(val exception: Throwable) : DataResult<T>()
}