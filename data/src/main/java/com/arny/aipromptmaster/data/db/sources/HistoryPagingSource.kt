package com.arny.aipromptmaster.data.db.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.arny.aipromptmaster.data.db.daos.PromptHistoryDao
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.domain.models.Prompt

class HistoryPagingSource(
    private val dao: PromptHistoryDao,
    private val promptId: String
) : PagingSource<Int, Prompt>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Prompt> {
        val page = params.key ?: 0
        return try {
            val entities = dao.getPromptHistoryPaged(
                promptId = promptId,
                limit = params.loadSize,
                offset = page * params.loadSize
            )
            val items = entities.map { it.toDomain() }
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Prompt>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
} 