package com.arny.aipromptmaster.domain.results

sealed class DataResult<out T> {
    data object Loading : DataResult<Nothing>()
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error(val exception: Throwable?, val message: String? = null) : DataResult<Nothing>()
}