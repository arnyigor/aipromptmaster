package com.arny.aipromptmaster.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.aipromptmaster.domain.models.Pageable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PageableAdapter<T : Any>(
    private val pageable: Pageable<T>
) : PagingSource<Int, T>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 0
        return try {
            suspendCancellableCoroutine { continuation ->
                pageable.load(
                    pageSize = params.loadSize,
                    pageIndex = page,
                    onSuccess = { items, hasNextPage ->
                        continuation.resume(
                            LoadResult.Page(
                                data = items,
                                prevKey = if (page == 0) null else page - 1,
                                nextKey = if (hasNextPage) page + 1 else null
                            )
                        )
                    },
                    onError = { error ->
                        continuation.resumeWithException(error)
                    }
                )
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
} 