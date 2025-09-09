package com.arny.aipromptmaster.presentation.ui.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class FilterState(
    val searchQuery: String = "",
    val sortOptions: List<SortCriteria> = listOf(
        SortCriteria(SortType.BY_DATE, SortDirection.DESC), // По умолчанию: новые сверху
        SortCriteria(SortType.BY_PRICE, SortDirection.ASC)  // По умолчанию: дешевые сверху
    ),
    val showOnlySelected: Boolean = false,
    val showOnlyFree: Boolean = true, // По умолчанию показываем только бесплатные
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
