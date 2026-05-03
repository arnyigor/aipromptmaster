package com.arny.aipromptmaster.domain.models

data class ModelsFilter(
    val query: String = "",
    val isFreeOnly: Boolean = false,
    val isFavoritesOnly: Boolean = false,
    val isAvailableOnly: Boolean = false,
    val sortType: SortType = SortType.RATING
)

enum class SortType { RATING, FAVORITE, NAME, CONTEXT, PRICE, AVAILABILITY, CHECKED, AVAILABLE_FIRST }
