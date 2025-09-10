package com.arny.aiprompts.presentation.ui.scraper

import com.arny.aiprompts.data.scraper.PreScrapeCheck
import com.arny.aiprompts.domain.model.PromptData
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface ScraperComponent {
    val state: StateFlow<ScraperState>

    // События от UI
    fun onPagesChanged(pages: String)
    fun onStartScrapingClicked()
    fun onParseAndSaveClicked()
    fun onOpenDirectoryClicked()
    fun onNavigateToImporterClicked()
    fun onBackClicked()

    // События от диалога
    fun onOverwriteConfirmed()
    fun onContinueConfirmed()
    fun onDialogDismissed()
}

/**
 * Data-класс, представляющий полное состояние экрана скрапера (ScraperScreen).
 * Является единым источником правды для UI.
 *
 * @property pagesToScrape Строковое значение из поля ввода "Количество страниц".
 * @property logs Список сообщений для отображения в панели логов.
 * @property savedHtmlFiles Список уже скачанных HTML-файлов, найденных на диске.
 * @property parsedPrompts Список промптов, извлеченных из HTML после парсинга.
 * @property lastSavedJsonFiles Список JSON-файлов, созданных в последней сессии сохранения.
 * @property inProgress Флаг, указывающий, что идет длительная операция (скрапинг или парсинг).
 * @property preScrapeCheckResult Результат предварительной проверки файлов. Если не null, показывается диалог.
 */
data class ScraperState(
    // --- Состояние полей ввода ---
    val pagesToScrape: String = "10",

    // --- Данные для отображения ---
    val logs: List<String> = listOf("Готов к запуску."),
    val savedHtmlFiles: List<File> = emptyList(),
    val parsedPrompts: List<PromptData> = emptyList(),
    val lastSavedJsonFiles: List<File> = emptyList(),

    // --- Состояние UI ---
    val inProgress: Boolean = false,
    val preScrapeCheckResult: PreScrapeCheck? = null
)