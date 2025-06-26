package com.arny.aipromptmaster.domain.results

sealed class DataResult<T> {
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error<T>(val exception: Throwable) : DataResult<T>()
    data class Loading<T>(val isLoading: Boolean = true) : DataResult<T>()
}