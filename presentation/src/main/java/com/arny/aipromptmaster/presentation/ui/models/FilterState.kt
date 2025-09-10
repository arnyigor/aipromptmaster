package com.arny.aipromptmaster.presentation.ui.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class FilterState(
    val searchQuery: String = "",
    val sortOptions: List<SortCriteria> = emptyList(),
    val showOnlySelected: Boolean = false,
    val showOnlyFavorites: Boolean = false,
    val showOnlyFree: Boolean = true,
    val requiredModalities: Set<ModalityType> = setOf(ModalityType.TEXT)
) : Parcelable

@Parcelize
data class SortCriteria(
    val type: SortType,
    val direction: SortDirection
) : Parcelable

enum class SortType(val displayName: String) {
    NONE("По умолчанию"),
    BY_NAME("По имени"),
    BY_PRICE("По цене"),
    BY_DATE("По дате"),
    BY_CONTEXT("По контексту")
}

enum class SortDirection(val symbol: String) {
    ASC("↑"), // По возрастанию
    DESC("↓") // По убыванию
}

enum class ModalityType {
    TEXT, IMAGE, AUDIO, VIDEO;

    companion object {
        fun fromApiString(apiString: String): ModalityType? {
            return when (apiString.lowercase()) {
                "text" -> TEXT
                "image" -> IMAGE
                "audio" -> AUDIO
                "video" -> VIDEO
                else -> null
            }
        }
    }
}
