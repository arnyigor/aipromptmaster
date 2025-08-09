package com.arny.aiprompts.presentation.ui.scraper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.domain.model.ScrapedPost
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ScraperResult
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin

@Composable
fun ScraperTestScreen() {
    val scrapeUseCase: ScrapeWebsiteUseCase = getKoin().get()
    val scope = rememberCoroutineScope()
    val pagesCount = 1
    var logs by remember { mutableStateOf(listOf("Готов к запуску.")) }
    var posts by remember { mutableStateOf<List<ScrapedPost>?>(null) }
    var inProgress by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = {
                scope.launch {
                    inProgress = true
                    logs = emptyList()
                    posts = null

                    scrapeUseCase("https://example", pagesCount)
                        .collect { result ->
                            when (result) {
                                is ScraperResult.InProgress -> {
                                    logs = logs + result.message
                                }
                                is ScraperResult.Success -> {
                                    posts = result.posts
                                    logs = logs + "--- УСПЕШНО ЗАВЕРШЕНО ---"
                                    inProgress = false
                                }
                                is ScraperResult.Error -> {
                                    logs = logs + "--- ОШИБКА: ${result.errorMessage} ---"
                                    inProgress = false
                                }
                            }
                        }
                }
            },
            enabled = !inProgress
        ) {
            Text("Запустить скрапер ($pagesCount страниц(ы))")
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxSize()) {
            // Панель логов
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(logs) { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
            }
            // Панель результатов
            posts?.let {
                println("posts:$posts")
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { Text("Найдено постов: ${it.size}", style = MaterialTheme.typography.titleMedium) }
                    items(it) { post ->
                        Text("Автор: ${post.author}")
                    }
                }
            }
        }
    }
}