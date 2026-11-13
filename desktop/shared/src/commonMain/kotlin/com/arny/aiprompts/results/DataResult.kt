package com.arny.aiprompts.results

sealed class DataResult<out T> {
    data object Loading : DataResult<Nothing>()
    data class Success<T>(val data: T) : DataResult<T>()
    data class Error<T>(val exception: Throwable?, val messageRes: String? = null) : DataResult<T>()
}