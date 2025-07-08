package com.arny.aipromptmaster.presentation.ui.home

sealed class PromptsUiEvents {
    data class OpenSortScreenEvent(
        val sortData: SortData,
        val currentFilters: CurrentFilters
    ) : PromptsUiEvents()
}