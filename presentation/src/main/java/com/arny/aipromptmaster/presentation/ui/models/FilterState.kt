package com.arny.aipromptmaster.presentation.ui.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FilterState(
    val searchQuery: String = "",
    val sortBy: SortOption = SortOption.NONE,
    val showOnlySelected: Boolean = false
):Parcelable

enum class SortOption {
    NONE,
    NEWEST,
    BY_NAME_ASC,
    BY_NAME_DESC,
}