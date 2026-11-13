package com.arny.aiprompts.presentation.ui.scraperwizard

import com.arny.aiprompts.domain.index.model.ParsedIndex
import com.arny.aiprompts.domain.index.model.PostLocation
import com.arny.aiprompts.domain.interfaces.PreScrapeCheck
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.usecase.ImportParsedPromptsUseCase
import kotlinx.coroutines.flow.StateFlow

/**
 * Шаги мастера скрапинга.
 */
enum class WizardStep {
    PAGE_INPUT,  // Ввод/просмотр страниц
    RESOLVING,   // Загрузка индекса и разрешение страниц
    ANALYSIS,    // Анализ (парсинг промптов)
    IMPORT       // Выбор и импорт
}

/**
 * Статус загрузки страницы.
 */
enum class DownloadStatus {
    PENDING, DOWNLOADING, SUCCESS, FAILED
}

/**
 * Прогресс разрешения ссылок из индекса.
 */
data class ResolvingProgress(
    val totalLinks: Int = 0,
    val resolved: Int = 0,
    val currentPostId: String? = null,
    val isRunning: Boolean = false
)

/**
 * Прогресс анализа промптов.
 */
data class AnalysisProgress(
    val totalPrompts: Int = 0,
    val processed: Int = 0,
    val newPrompts: Int = 0,
    val duplicates: Int = 0,
    val errors: Int = 0,
    val isRunning: Boolean = false
)

/**
 * Ошибка на определенном шаге мастера.
 */
data class ScraperError(
    val step: WizardStep,
    val message: String,
    val details: String? = null
)

/**
 * Состояние мастера скрапинга.
 */
data class ScraperWizardState(
    val currentStep: WizardStep = WizardStep.PAGE_INPUT,
    val pagesInput: String = "",
    val page1Exists: Boolean = false,
    val pageCheckResult: PreScrapeCheck? = null,
    val pagesToDownload: List<Int> = emptyList(),
    val downloadProgress: Map<Int, DownloadStatus> = emptyMap(),
    val isDownloading: Boolean = false,

    // Индекс (для категорий)
    val parsedIndex: ParsedIndex? = null,

    // Разрешение ссылок
    val resolvingProgress: ResolvingProgress = ResolvingProgress(),
    val postLocations: List<PostLocation> = emptyList(),

    // Промпты
    val indexPrompts: List<PromptData> = emptyList(),
    val selectedPrompts: Set<String> = emptySet(),
    val analysisProgress: AnalysisProgress = AnalysisProgress(),

    // Выбранные для импорта
    val importResult: ImportParsedPromptsUseCase.ImportResult? = null,

    val errorMessages: List<ScraperError> = emptyList(),
    val logs: List<String> = emptyList(),

    val isEditingPrompt: Boolean = false,
    val draftPrompt: PromptData? = null
)

/**
 * Компонент мастера скрапинга.
 */
interface ScraperWizardComponent {
    val state: StateFlow<ScraperWizardState>

    // ========== Step 1: Page Input ==========
    fun onPagesInputChanged(input: String)
    fun onCheckPagesClicked()

    // ========== Step 2: Resolving (Index parsing + page location resolution) ==========
    fun onStartResolving()

    // ========== Step 3: Analysis (parsing prompts from index) ==========
    fun onStartAnalysisClicked()

    // ========== Step 4: Import ==========
    fun onTogglePromptSelection(promptId: String, selected: Boolean)
    fun onSelectAllPrompts(selected: Boolean)
    fun onImportSelectedClicked()

    // ========== Preview ==========
    fun onPromptPreviewClicked(prompt: PromptData)

    // ========== Navigation ==========
    fun onBackToPreviousStep()
    fun onNextStep()
    fun onFinish()

    // ========== Error Handling ==========
    fun onCopyErrorsToClipboard()
    fun onDismissError(error: ScraperError)
    fun onPromptEdited(updatedPrompt: PromptData)
}
