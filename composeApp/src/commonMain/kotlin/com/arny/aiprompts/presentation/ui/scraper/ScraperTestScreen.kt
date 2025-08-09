package com.arny.aiprompts.presentation.ui.scraper

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ScraperResult
import com.arny.aiprompts.data.scraper.PreScrapeCheck
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.usecase.ParseHtmlUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.arny.aiprompts.presentation.utils.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File

@Composable
fun ScraperTestScreen() {
    val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase = getKoin().get()
    var lastSavedJsonFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    val parseUseCase: ParseHtmlUseCase = getKoin().get()
    var parsedPrompts by remember { mutableStateOf<List<PromptData>>(emptyList()) }
    val scrapeUseCase: ScrapeWebsiteUseCase = getKoin().get()
    val webScraper: WebScraper = getKoin().get()
    val scope = rememberCoroutineScope()

    // --- Состояния UI ---
    var pagesToScrape by remember { mutableStateOf("10") }
    var logs by remember { mutableStateOf(listOf("Готов к запуску.")) }
    // Инициализируем `savedFiles` пустым списком, он будет заполнен в LaunchedEffect
    var savedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var inProgress by remember { mutableStateOf(false) }
    var preScrapeCheckResult by remember { mutableStateOf<PreScrapeCheck?>(null) }
    val showDialog by remember { derivedStateOf { preScrapeCheckResult != null } }

    val logListState = rememberLazyListState()

    // --- НОВЫЙ БЛОК: Загрузка существующих файлов при первом запуске экрана ---
    LaunchedEffect(Unit) {
        // Запускаем в корутине, т.к. работа с файлами может быть блокирующей
        savedFiles = webScraper.getExistingScrapedFiles()
    }

    // Функция для запуска скрапинга, теперь принимает только стартовую страницу
    val startScraping = { pages: List<Int> ->
        scope.launch {
            inProgress = true
            // Очищаем лог только при полном перезапуске
            if (pages.size == (pagesToScrape.toIntOrNull() ?: 0)) {
                logs = listOf("Запуск перезаписи...")
                savedFiles = emptyList()
            }

            scrapeUseCase("https://4pda.to/forum/index.php?showtopic=1109539", pages).collect { result ->
                when (result) {
                    is ScraperResult.InProgress -> {
                        logs = logs + result.message
                        logListState.animateScrollToItem(logs.size)
                    }

                    is ScraperResult.Success -> {
                        // --- ОБНОВЛЕННАЯ ЛОГИКА ---
                        // Просто перезагружаем список файлов из директории,
                        // чтобы получить самое актуальное состояние
                        savedFiles = webScraper.getExistingScrapedFiles()

                        logs = logs + "--- ЗАВЕРШЕНО ---"
                        inProgress = false
                    }

                    is ScraperResult.Error -> {
                        logs = logs + "--- ОШИБКА: ${result.errorMessage} ---"
                        inProgress = false
                    }
                }
            }
        }
    }

    // --- Функция, которая вызывается по кнопке "Запустить" ---
    val onStartClicked = {
        val totalPages = pagesToScrape.toIntOrNull() ?: 0
        if (totalPages > 0) {
            scope.launch {
                val checkResult = webScraper.checkExistingFiles(totalPages)
                // Показываем диалог, если есть и существующие, и отсутствующие файлы
                if (checkResult.existingFileCount > 0 && checkResult.missingPages.isNotEmpty()) {
                    preScrapeCheckResult = checkResult
                } else {
                    // Иначе просто скачиваем все (или все недостающие, если есть только они)
                    startScraping((0 until totalPages).toList())
                }
            }
        }
    }

    // --- UI ---
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = pagesToScrape,
                onValueChange = { pagesToScrape = it.filter { c -> c.isDigit() } },
                label = { Text("Количество страниц") },
                modifier = Modifier.width(180.dp)
            )
            Button(
                onClick = onStartClicked,
                enabled = !inProgress && (pagesToScrape.toIntOrNull() ?: 0) > 0
            ) {
                Text("Запустить скрапер")
            }
            // --- НОВАЯ КНОПКА ---
            IconButton(
                onClick = {
                    // Вызываем метод напрямую, т.к. он не suspend и очень быстрый
                    try {
                        webScraper.openSaveDirectory()
                    } catch (e: Exception) {
                        // На случай, если что-то пойдет не так
                        e.printStackTrace()
                        // Можно показать Snackbar с ошибкой
                    }
                },
                enabled = !inProgress // Отключаем во время работы скрапера
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Открыть директорию с файлами"
                )
            }
            // --- КОНЕЦ НОВОЙ КНОПКИ ---
            if (inProgress) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            Button(
                onClick = {
                    scope.launch {
                        // Очищаем предыдущие результаты
                        lastSavedJsonFiles = emptyList()
                        parsedPrompts = emptyList()
                        inProgress = true // Показываем индикатор на время парсинга
                        logs = logs + "--- НАЧИНАЮ ПАРСИНГ ВСЕХ ФАЙЛОВ ---"

                        // Получаем количество страниц из поля ввода, чтобы не парсить лишнего
                        val pageCount = pagesToScrape.toIntOrNull() ?: savedFiles.size
                        val filesToParse = savedFiles.take(pageCount)

                        // Используем coroutineScope для запуска параллельных задач
                        val parsingResults = coroutineScope {
                            filesToParse.map { file ->
                                // async запускает каждую задачу в отдельной корутине
                                async(Dispatchers.IO) {
                                    // Dispatchers.IO, т.к. чтение файла - блокирующая операция
                                    logs = logs + "Парсинг ${file.absolutePath}..."
                                    // Вызываем UseCase для каждого файла
                                    parseUseCase(file)
                                }
                            }.awaitAll() // Ждем завершения всех задач парсинга
                        }

                        // Теперь обрабатываем результаты
                        val allParsedPrompts = mutableListOf<PromptData>()
                        var successCount = 0
                        var failureCount = 0

                        parsingResults.forEach { result ->
                            result.onSuccess { promptsData ->
                                allParsedPrompts.addAll(promptsData)
                                successCount++
                            }
                                .onFailure { error ->
                                    logs = logs + "ОШИБКА: ${error.message}"
                                    failureCount++
                                }
                        }

                        logs = logs + "--- ПАРСИНГ ЗАВЕРШЕН ---"
                        logs = logs + "Успешно обработано файлов: $successCount, с ошибками: $failureCount."
                        logs = logs + "Всего найдено промптов: ${allParsedPrompts.size}."

                        parsedPrompts = allParsedPrompts

                        // Если что-то удалось спарсить, сохраняем это в JSON-файлы
                        if (allParsedPrompts.isNotEmpty()) {
                            logs = logs + "Сохранение ${allParsedPrompts.size} промптов в JSON файлы..."
                            savePromptsAsFilesUseCase(allParsedPrompts)
                                .onSuccess { savedJsonFiles ->
                                    lastSavedJsonFiles = savedJsonFiles
                                    logs = logs + "Успешно сохранено ${savedJsonFiles.size} файлов."
                                }
                                .onFailure { saveError ->
                                    logs = logs + "Ошибка сохранения файлов: ${saveError.message}"
                                }
                        }

                        inProgress = false // Скрываем индикатор

                        println(logs)
                    }
                },
                enabled = savedFiles.isNotEmpty()
            ) {
                Text("Парсить и сохранить в JSON")
            }
        }

        // Логи и результаты
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Панель логов
            LazyColumn(modifier = Modifier.weight(1f), state = logListState) {
                items(logs) { log -> Text(log, style = MaterialTheme.typography.bodySmall) }
            }
            // Панель результатов
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        "Сохраненные файлы (${savedFiles.size} шт.):",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(savedFiles) { file -> Text("• ${file.name}") }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        "Спарсенные промпты (${parsedPrompts.size}):",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(parsedPrompts) { prompt ->
                    Text("• ${prompt.title}", style = MaterialTheme.typography.bodySmall)
                }

                item { Spacer(Modifier.height(16.dp)) }

                item {
                    Text(
                        "Сохраненные JSON файлы (${lastSavedJsonFiles.size}):",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(lastSavedJsonFiles) { file ->
                    Text("• ${file.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // --- НОВЫЙ УЛУЧШЕННЫЙ ДИАЛОГ ---
    if (showDialog) {
        val checkResult = preScrapeCheckResult
        if (checkResult != null) {
            AlertDialog(
                onDismissRequest = { preScrapeCheckResult = null; inProgress = false },
                title = { Text("Обнаружены пропуски") },
                text = {
                    Text(
                        "Найдено ${checkResult.existingFileCount} из ${pagesToScrape} страниц.\n" +
                                "Отсутствуют страницы: ${
                                    checkResult.missingPages.map { it + 1 }.joinToString()
                                }. \n\n" +
                                "Что вы хотите сделать?"
                    )
                },
                confirmButton = {
                    // Кнопка "Докачать недостающие"
                    Button(
                        onClick = {
                            preScrapeCheckResult = null
                            // Запускаем скрапинг, передавая только список недостающих страниц
                            startScraping(checkResult.missingPages)
                        }
                    ) {
                        Text("Докачать (${checkResult.missingPages.size})")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            preScrapeCheckResult = null
                            // Запускаем скрапинг для ВСЕХ страниц от 0 до N
                            startScraping((0 until (pagesToScrape.toIntOrNull() ?: 0)).toList())
                        }) { Text("Перезаписать все") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            preScrapeCheckResult = null
                            inProgress = false
                        }) { Text("Отмена") }
                    }
                }
            )
        }
    }
}
