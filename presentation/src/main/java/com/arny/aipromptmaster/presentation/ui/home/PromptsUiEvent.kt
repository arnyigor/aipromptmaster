package com.arny.aipromptmaster.presentation.ui.home

import com.arny.aipromptmaster.domain.models.SyncConflict
import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString

sealed class PromptsUiEvent {
    data class SyncSuccess(val updatedCount: Int) : PromptsUiEvent()
    data object SyncError : PromptsUiEvent()
    data class SyncConflicts(val conflicts: List<SyncConflict>) : PromptsUiEvent()
    data object SyncInProgress : PromptsUiEvent()
    data class ShowError(val message: IWrappedString) : PromptsUiEvent()
    data object PromptUpdated : PromptsUiEvent()
    data class OpenSortScreenEvent(val sortData: SortData, val currentFilters: CurrentFilters) : PromptsUiEvent()
}