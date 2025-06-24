package com.arny.aipromptmaster.domain.results

sealed class LLMResult<T> {
    data class Success<T>(val data: T) : LLMResult<T>()
    data class Error<T>(val exception: Throwable) : LLMResult<T>()
    data class Loading<T>(val isLoading: Boolean = true) : LLMResult<T>()
}