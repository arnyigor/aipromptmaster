package com.arny.aiprompts.presentation.ui.importer

import com.arny.aiprompts.domain.model.RawPostData
import java.io.File

// Перечисление для различных этапов импорта
enum class ImportStep {
    LOADING_FILES,    // Загрузка и парсинг файлов
    SELECTING_POSTS,  // Выбор постов для импорта
    EDITING_CONTENT,  // Редактирование контента
    GENERATING_JSON   // Генерация финальных файлов
}

// Состояние процесса импорта
data class ImportProgress(
    val currentStep: ImportStep = ImportStep.LOADING_FILES,
    val stepProgress: Float = 0f, // 0.0 - 1.0
    val currentItem: String = "",
    val totalItems: Int = 0,
    val processedItems: Int = 0
)

// Фильтры для списка постов
data class PostFilters(
    val searchQuery: String = "",
    val showOnlyReady: Boolean = false,
    val showOnlyLikelyPrompts: Boolean = false,
    val selectedCategory: String? = null
)

// Группировка постов
enum class PostGrouping {
    NONE, BY_AUTHOR, BY_CATEGORY, BY_DATE
}

// Состояние загрузки файла
enum class DownloadState {
    NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, ERROR
}

// Информация о загруженном файле
data class DownloadedFile(
    val url: String,
    val filename: String,
    val content: String? = null, // Содержимое файла (для текстовых файлов)
    val localPath: String? = null, // Локальный путь к файлу
    val state: DownloadState = DownloadState.NOT_DOWNLOADED,
    val error: String? = null
)

data class ImporterState(
    // Основное состояние
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // Данные
    val sourceHtmlFiles: List<File> = emptyList(),
    val rawPosts: List<RawPostData> = emptyList(),

    // Навигация и выбор
    val selectedPostId: String? = null,
    val postsToImport: Set<String> = emptySet(),

    // Категории
    val availableCategories: List<String> = emptyList(),

    // Фильтры и группировка
    val filters: PostFilters = PostFilters(),
    val grouping: PostGrouping = PostGrouping.NONE,

    // Прогресс
    val progress: ImportProgress = ImportProgress(),

    // Хранилище отредактированных данных
    val editedData: Map<String, EditedPostData> = emptyMap(),

    // UI состояние
    val showPreview: Boolean = false,
    val expandedPostIds: Set<String> = emptySet(),
    val validationErrors: Map<String, Map<String, String>> = emptyMap(),

    // Работа с файлами
    val downloadedFiles: Map<String, DownloadedFile> = emptyMap(), // URL -> DownloadedFile
    val expandedFileIds: Set<String> = emptySet(), // ID развернутых файлов
    val savedFiles: Map<String, String> = emptyMap() // postId -> filePath
) {
    // Вычисляемые свойства
    val selectedPost: RawPostData?
        get() = rawPosts.find { it.postId == selectedPostId }

    val currentEditedData: EditedPostData?
        get() = selectedPostId?.let { editedData[it] }

    val filteredPosts: List<RawPostData>
        get() = rawPosts.filter { post ->
            // Поисковый фильтр
            (filters.searchQuery.isBlank() ||
             post.fullHtmlContent.contains(filters.searchQuery, ignoreCase = true) ||
             post.author.name.contains(filters.searchQuery, ignoreCase = true))

            // Фильтр по готовности
            && (!filters.showOnlyReady || post.postId in postsToImport)

            // Фильтр по вероятности промпта
            && (!filters.showOnlyLikelyPrompts || post.isLikelyPrompt)
        }

    val readyToImportCount: Int
        get() = postsToImport.size

    val hasValidationErrors: Boolean
        get() = validationErrors.isNotEmpty()

    val canGenerateJson: Boolean
        get() = postsToImport.isNotEmpty() && !hasValidationErrors && !isLoading
}