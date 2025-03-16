package com.arny.aipromptmaster.presentation.ui.home

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.Prompt

class PromptPagingSource(
    private val interactor: IPromptsInteractor,
    private val query: String = "",
    private val category: String? = null,
    private val status: String? = null,
    private val tags: List<String> = emptyList()
) : PagingSource<Int, Prompt>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Prompt> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize

            val prompts = interactor.getPrompts(
                query = query,
                category = category,
                status = status,
                tags = tags,
                offset = page * pageSize,
                limit = pageSize
            )

            LoadResult.Page(
                data = prompts,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (prompts.size < pageSize) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Prompt>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
} 