package com.arny.aipromptmaster.presentation.ui.home

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// Данные, которые BottomSheet возвращает
@Parcelize
data class FilterSettings(
    val category: String?,
    val tags: List<String>,
) : Parcelable

// Текущее состояние фильтров
@Parcelize
data class CurrentFilters(
    val category: String?,
    val tags: List<String>,
) : Parcelable

// Данные, которые нужны BottomSheet
@Parcelize
data class SortData(
    val categories: List<String>,
    val tags: List<String>,
) : Parcelable
