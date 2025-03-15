package com.arny.aipromptmaster.domain.models

interface Pageable<T> {
    suspend fun load(
        pageSize: Int,
        pageIndex: Int,
        onSuccess: (items: List<T>, hasNextPage: Boolean) -> Unit,
        onError: (Throwable) -> Unit
    )
} 