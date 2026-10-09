package com.arny.aiprompts.data.remote

import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.utils.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode

/** Connection diagnostics for settings. Public publication is owned by the catalog workflow. */
class GitHubSyncService(
    private val httpClient: HttpClient,
    private val settingsRepository: ISettingsRepository,
    val vaultLocal: com.arny.promptcontract.PersonalVaultLocal
) {
    private val baseUrl = "https://api.github.com"

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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, "GitHubSync", "Connection check failed")
            false
        }
    }

}
