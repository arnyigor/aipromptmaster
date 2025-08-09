package com.arny.aiprompts.presentation.ui.scraper

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
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ScraperResult
import com.arny.aiprompts.data.scraper.PreScrapeCheck
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File

@Composable
fun ScraperTestScreen() {
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
    val startScraping = { startPage: Int ->
        scope.launch {
            inProgress = true
            if (startPage == 0) {
                logs = listOf("Запуск с нуля...")
                // При перезаписи мы не очищаем `savedFiles` сразу,
                // он сам обновится по мере сохранения новых файлов.
                // Либо можно очистить для мгновенной обратной связи:
                savedFiles = emptyList()
            }

            scrapeUseCase("https://4pda.to/forum/index.php?showtopic=1109539", pagesToScrape.toIntOrNull() ?: 1, startPage)
                .collect { result ->
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
            // Выполняем предварительную проверку в фоновом потоке, чтобы не блокировать UI
            scope.launch {
                val checkResult = webScraper.checkExistingFiles(totalPages)
                if (checkResult.existingFileCount > 0) {
                    // Если есть файлы, показываем диалог
                    preScrapeCheckResult = checkResult
                } else {
                    // Если файлов нет, просто запускаем с самого начала
                    startScraping(0)
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
        }

        // Логи и результаты
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Панель логов
            LazyColumn(modifier = Modifier.weight(1f), state = logListState) {
                items(logs) { log -> Text(log, style = MaterialTheme.typography.bodySmall) }
            }
            // Панель результатов
            LazyColumn(modifier = Modifier.weight(1f)) {
                item { Text("Сохраненные файлы (${savedFiles.size} шт.):", style = MaterialTheme.typography.titleMedium) }
                items(savedFiles) { file -> Text("• ${file.name}") }
            }
        }
    }

    // --- НОВЫЙ УЛУЧШЕННЫЙ ДИАЛОГ ---
    if (showDialog) {
        // ИСПРАВЛЕНИЕ: Получаем результат проверки напрямую
        val checkResult = preScrapeCheckResult
        if (checkResult != null) {
            AlertDialog(
                onDismissRequest = { preScrapeCheckResult = null; inProgress = false },
                title = { Text("Обнаружены файлы") },
                text = { Text("Найдено ${checkResult.existingFileCount} уже скачанных страниц из ${pagesToScrape}.\n\nЧто вы хотите сделать?") },
                confirmButton = {
                    if (checkResult.canContinue) {
                        Button(onClick = {
                            preScrapeCheckResult = null
                            startScraping(checkResult.existingFileCount)
                        }) { Text("Продолжить") }
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            preScrapeCheckResult = null
                            startScraping(0)
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