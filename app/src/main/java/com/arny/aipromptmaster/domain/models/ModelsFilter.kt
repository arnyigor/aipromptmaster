package com.arny.aipromptmaster.domain.models

data class ModelsFilter(
    val query: String = "",
    val isFreeOnly: Boolean = false,
    val isFavoritesOnly: Boolean = false,
    val sortType: SortType = SortType.FAVORITE
)

enum class SortType { FAVORITE, NAME, CONTEXT, PRICE }