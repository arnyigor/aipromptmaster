package com.arny.aipromptmaster.data.repositories

import android.content.Context
import android.content.pm.PackageManager
import com.arny.aipromptmaster.data.BuildConfig
import com.arny.aipromptmaster.data.api.FeedbackApiService
import com.arny.aipromptmaster.data.models.AppInfoDto
import com.arny.aipromptmaster.data.models.FeedbackRequestDto
import com.arny.aipromptmaster.domain.repositories.IFeedbackRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepositoryImpl @Inject constructor(
    private val apiService: FeedbackApiService,
    private val context: Context,
) : IFeedbackRepository {

    // Вынесем создание AppInfoDto в приватную функцию для чистоты
    private fun getAppInfo(): AppInfoDto {
        val packageName = context.packageName
        val packageManager = context.packageManager

        // Безопасно получаем PackageInfo
        val packageInfo = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            // Этот случай маловероятен, но лучше обработать
            null
        }

        val versionName = packageInfo?.versionName ?: "N/A"
        val appName = context.applicationInfo.loadLabel(packageManager).toString()

        return AppInfoDto(
            name = appName,
            id = "ai-prompt-master-android", // Это поле, скорее всего, останется статичным
            version = versionName,
            packageName = packageName
        )
    }

    override suspend fun sendFeedback(content: String): Result<Unit> {
        val appInfo = getAppInfo()
        return try {
            val request = FeedbackRequestDto(appInfo = appInfo, content = content)
            val response = apiService.sendFeedback(request)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                // Ошибка сервера (4xx, 5xx)
                val errorMsg = "Ошибка сервера: ${response.code()} ${response.message()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            // Сетевая ошибка или другая проблема
            Result.failure(e)
        }
    }
}
