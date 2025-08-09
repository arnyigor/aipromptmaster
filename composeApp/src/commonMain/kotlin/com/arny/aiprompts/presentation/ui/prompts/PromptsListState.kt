package com.arny.aiprompts.presentation.ui.prompts

import com.arny.aiprompts.domain.model.Prompt

// Добавляем enum для удобной и типобезопасной работы с сортировкой
enum class SortOrder(val title: String) {
    BY_FAVORITE_DESC("Сначала избранное"),
    BY_NAME_ASC("По названию"),
    BY_DATE_DESC("По дате создания"),
    BY_CATEGORY("По категории")
}

data class PromptsListState(
    // Основные состояния
    val isLoading: Boolean = false,
    val error: String? = null,

    // Данные
    val allPrompts: List<Prompt> = emptyList(),
    val currentPrompts: List<Prompt> = emptyList(),
    val page: Int = 0,
    val isPageLoading: Boolean = false,
    val endReached: Boolean = false,

    // Состояние фильтров и сортировки
    val searchQuery: String = "",
    val selectedCategory: String = "Все категории",
    val isSortAscending: Boolean = false,
    val selectedSortOrder: SortOrder = SortOrder.BY_FAVORITE_DESC,
    val isFavoritesOnly: Boolean = false, // <-- Замена для старого enum Filter

    // Данные для UI (выпадающие списки)
    val availableCategories: List<String> = listOf("Все категории", "Разработка", "Маркетинг", "Дизайн"),
    val availableSortOrders: List<SortOrder> = SortOrder.entries, // .values() устарело

    // Состояние для правой панели
    val selectedPromptId: String? = null,
    val isMoreMenuVisible: Boolean = false // Для управления выпадающим меню на mobile
)