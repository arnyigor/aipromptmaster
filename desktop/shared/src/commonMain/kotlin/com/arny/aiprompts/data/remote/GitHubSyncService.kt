package com.arny.aiprompts.data.remote

import com.arny.aiprompts.data.repositories.ISettingsRepository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.utils.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.encodeBase64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Сервис для синхронизации промптов с GitHub через REST API.
 * 
 * Позволяет:
 * - Проверять соединение с GitHub
 * - Загружать промпты в репозиторий (push)
 * - Получать список промптов из репозитория
 * - Синхронизировать изменения
 * 
 * Для работы требуется Personal Access Token с правами 'repo'.
 */
class GitHubSyncService(
    private val httpClient: HttpClient,
    private val settingsRepository: ISettingsRepository,
    private val json: Json
) {
    private val baseUrl = "https://api.github.com"

    /**
     * Проверяет соединение с GitHub и права доступа к репозиторию.
     * 
     * @return true если соединение успешно и токен работает
     */
    suspend fun checkConnection(): Boolean {
        val token = settingsRepository.getGitHubToken() ?: return false
        val repo = settingsRepository.getGitHubRepo() ?: return false
        
        return try {
            Logger.d("GitHubSync", "Checking connection to repo: $repo")
            val response = httpClient.get("$baseUrl/repos/$repo") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github.v3+json")
            }
            val success = response.status == HttpStatusCode.OK
            Logger.d("GitHubSync", "Connection check result: $success (${response.status})")
            success
        } catch (e: Exception) {
            Logger.e(e, "GitHubSync", "Connection check failed")
            false
        }
    }

    /**
     * Загружает промпт в репозиторий (создает или обновляет файл).
     * Файл сохраняется в папке prompts/{category}/{id}.json
     * 
     * @param prompt Промпт для загрузки
     * @return true если операция успешна
     */
    suspend fun pushPrompt(prompt: PromptData): Result<Unit> {
        val token = settingsRepository.getGitHubToken() 
            ?: return Result.failure(IllegalStateException("GitHub token not set"))
        val repo = settingsRepository.getGitHubRepo() 
            ?: return Result.failure(IllegalStateException("GitHub repo not set"))
        
        // Имя файла: очищаем заголовок от спецсимволов
        val safeTitle = prompt.title
            .replace(Regex("[^a-zA-Z0-9а-яА-Я _-]"), "")
            .trim()
            .replace(" ", "_")
            .take(50) // Ограничиваем длину
        
        val category = prompt.tags.firstOrNull()?.lowercase() ?: "general"
        val filePath = "prompts/$category/${safeTitle}_${prompt.id.take(8)}.json"
        
        return try {
            Logger.d("GitHubSync", "Pushing prompt: ${prompt.title} to $filePath")
            
            // 1. Проверяем, существует ли файл (нужен SHA для обновления)
            var currentSha: String? = null
            try {
                val existingFile: GitHubFileContent = httpClient
                    .get("$baseUrl/repos/$repo/contents/$filePath") {
                        header("Authorization", "Bearer $token")
                    }.body()
                currentSha = existingFile.sha
                Logger.d("GitHubSync", "File exists, SHA: $currentSha")
            } catch (e: Exception) {
                // Файла нет, это нормально для создания
                Logger.d("GitHubSync", "File doesn't exist, will create new")
            }

            // 2. Подготавливаем контент
            val jsonContent = json.encodeToString(PromptData.serializer(), prompt)
            val base64Content = jsonContent.encodeBase64()

            // 3. Отправляем PUT запрос
            val commitMsg = if (currentSha == null) {
                "Create prompt: ${prompt.title}"
            } else {
                "Update prompt: ${prompt.title}"
            }
            
            val response = httpClient.put("$baseUrl/repos/$repo/contents/$filePath") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(GitHubCommitBody(
                    message = commitMsg,
                    content = base64Content,
                    sha = currentSha
                ))
            }
            
            if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                Logger.d("GitHubSync", "Push successful: ${response.status}")
                Result.success(Unit)
            } else {
                val error = "Push failed: ${response.status}"
                Logger.e("GitHubSync", error)
                Result.failure(IllegalStateException(error))
            }
        } catch (e: Exception) {
            Logger.e(e, "GitHubSync", "Push failed")
            Result.failure(e)
        }
    }

    /**
     * Получает список файлов промптов из репозитория.
     * 
     * @return Список файлов с информацией о содержимом
     */
    suspend fun fetchPromptsList(): List<GitHubFileContent> {
        val token = settingsRepository.getGitHubToken() ?: return emptyList()
        val repo = settingsRepository.getGitHubRepo() ?: return emptyList()

        return try {
            Logger.d("GitHubSync", "Fetching prompts list from repo: $repo")
            
            // Сначала получаем список категорий (папок)
            val categories: List<GitHubFileContent> = try {
                httpClient.get("$baseUrl/repos/$repo/contents/prompts") {
                    header("Authorization", "Bearer $token")
                }.body()
            } catch (e: Exception) {
                Logger.d("GitHubSync", "No prompts folder found or empty")
                return emptyList()
            }
            
            // Для каждой категории получаем файлы
            val allFiles = mutableListOf<GitHubFileContent>()
            categories.filter { it.type == "dir" }.forEach { category ->
                try {
                    val files: List<GitHubFileContent> = httpClient
                        .get("$baseUrl/repos/$repo/contents/${category.path}") {
                            header("Authorization", "Bearer $token")
                        }.body()
                    allFiles.addAll(files.filter { it.name.endsWith(".json") })
                } catch (e: Exception) {
                    Logger.w("GitHubSync", "Failed to fetch category ${category.name}: ${e.message}")
                }
            }
            
            Logger.d("GitHubSync", "Found ${allFiles.size} prompt files")
            allFiles
        } catch (e: Exception) {
            Logger.e(e, "GitHubSync", "Fetch list failed")
            emptyList()
        }
    }

    /**
     * Получает содержимое файла промпта.
     * 
     * @param fileContent Информация о файле (содержит path)
     * @return PromptData или null в случае ошибки
     */
    suspend fun fetchPromptContent(fileContent: GitHubFileContent): PromptData? {
        val token = settingsRepository.getGitHubToken() ?: return null
        val repo = settingsRepository.getGitHubRepo() ?: return null
        
        return try {
            // Получаем файл с содержимым
            val fileWithContent: GitHubFileContent = httpClient
                .get("$baseUrl/repos/$repo/contents/${fileContent.path}") {
                    header("Authorization", "Bearer $token")
                }.body()
            
            // Декодируем Base64 контент
            @OptIn(ExperimentalEncodingApi::class)
            val content = fileWithContent.content?.let { base64Content ->
                // Base64 от GitHub может содержать переносы строк
                val cleanBase64 = base64Content.replace("\n", "")
                Base64.decode(cleanBase64).decodeToString()
            } ?: return null
            
            // Парсим JSON
            json.decodeFromString(PromptData.serializer(), content)
        } catch (e: Exception) {
            Logger.e(e, "GitHubSync", "Failed to fetch content for ${fileContent.path}")
            null
        }
    }

    /**
     * Синхронизирует все локальные промпты с GitHub.
     * Загружает все промпты, которых еще нет в репозитории.
     * 
     * @param prompts Список локальных промптов для синхронизации
     * @return Результат синхронизации с деталями
     */
    suspend fun syncPrompts(prompts: List<PromptData>): SyncResult {
        var pushed = 0
        var failed = 0
        val errors = mutableListOf<String>()
        
        prompts.forEach { prompt ->
            pushPrompt(prompt).fold(
                onSuccess = { pushed++ },
                onFailure = { e ->
                    failed++
                    errors.add("${prompt.title}: ${e.message}")
                }
            )
        }
        
        return SyncResult(
            success = failed == 0,
            pushed = pushed,
            failed = failed,
            errors = errors
        )
    }
}

/**
 * Результат синхронизации.
 */
data class SyncResult(
    val success: Boolean,
    val pushed: Int,
    val failed: Int,
    val errors: List<String>
)

/**
 * Модель ответа GitHub API для содержимого файла.
 */
@Serializable
data class GitHubFileContent(
    val name: String,
    val path: String,
    val type: String,
    val sha: String? = null,
    val content: String? = null, // Base64 encoded
    val encoding: String? = null
)

/**
 * Тело запроса для создания/обновления файла в GitHub.
 */
@Serializable
data class GitHubCommitBody(
    val message: String,
    val content: String, // Base64 encoded content
    val sha: String? = null // Нужен для обновления существующего файла
)
