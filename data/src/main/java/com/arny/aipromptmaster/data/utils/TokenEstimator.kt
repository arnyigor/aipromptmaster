package com.arny.aipromptmaster.data.utils

import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.TokenEstimationResult
import com.arny.aipromptmaster.domain.utils.ITokenEstimator
import javax.inject.Inject
import kotlin.math.ceil

/**
 * Сервис для оценки количества токенов в тексте и контексте чата.
 * Использует приближенный расчет, который можно корректировать на основе реальных данных.
 */
class TokenEstimator @Inject constructor() : ITokenEstimator {

    companion object {
        // Стандартные коэффициенты: символов на токен.
        // Эти значения могут быть настройками, но для простоты зафиксированы.
        const val DEFAULT_TOKEN_RATIO = 3.5 // Для "нейтрального" или английского текста

        // Диапазон, в котором будет корректироваться коэффициент
        private const val MIN_RATIO = 2.0
        private const val MAX_RATIO = 8.0

        // Веса для плавной корректировки: 70% от старого, 30% от нового
        private const val CURRENT_RATIO_WEIGHT = 0.7
        private const val NEW_RATIO_WEIGHT = 0.3
    }

    // Текущий коэффициент для расчета. Используется для корректировки.
    @Volatile
    private var currentTokenRatio: Double = DEFAULT_TOKEN_RATIO

    @Volatile
    private var isAccurate: Boolean = false

    /**
     * Рассчитывает приблизительное количество токенов для заданных параметров.
     *
     * @param inputText Текст, введенный пользователем.
     * @param attachedFiles Список прикрепленных файлов (учитываются только текстовые).
     * @param systemPrompt Текущий системный промпт.
     * @param chatHistory Последние сообщения из истории чата.
     * @return Объект [TokenEstimationResult] с оценкой токенов и флагом точности.
     */
    override fun estimateTokens(
        inputText: String,
        attachedFiles: List<FileAttachment>,
        systemPrompt: String?,
        chatHistory: List<ChatMessage>,
    ): TokenEstimationResult {
        val ratio = currentTokenRatio

        var totalTokens = 0

        // 1. Токены от текста ввода
        if (inputText.isNotBlank()) {
            totalTokens += ceil(inputText.length / ratio).toInt()
        }

        // 2. Токены от прикрепленных файлов (только текстовые)
        attachedFiles.forEach { file ->
            if (file.mimeType.startsWith("text/")) {
                // Важно: учитываем только содержимое файла, которое будет отправлено.
                // В вашем `LLMInteractor` файлы могут быть обрезаны или обработаны.
                // Здесь учитывается `originalContent`. Если в `buildMessagesForApi` используется
                // другая логика (например, только превью или чанки), расчет может расходиться.
                // Это приближение.
                totalTokens += ceil(file.originalContent.length / ratio).toInt()
            }
        }

        // 3. Токены от системного промпта
        if (!systemPrompt.isNullOrBlank()) {
            totalTokens += ceil(systemPrompt.length / ratio).toInt()
        }

        // 4. Токены от истории сообщений (например, последние 10, как в предыдущем коде)
        // ВАЖНО: Это может не совпадать с `MAX_HISTORY_SIZE` из `LLMInteractor`, если там используется другое количество.
        chatHistory.takeLast(10).forEach { message ->
            totalTokens += ceil(message.content.length / ratio).toInt()
        }

        return TokenEstimationResult(
            estimatedTokens = totalTokens,
            isAccurate = this.isAccurate // Возвращаем текущий статус точности
        )
    }

    /**
     * Скорректировать коэффициент расчета на основе реальных данных из API.
     * Этот метод вызывается, когда становятся известны реальные токены.
     *
     * @param actualPromptTokens Реальное количество токенов во входящем сообщении/контексте.
     * @param estimatedPromptTokens Оценочное количество токенов во входящем сообщении/контексте.
     */
    override fun adjustTokenRatio(
        actualPromptTokens: Int,
        estimatedPromptTokens: Int
    ) {
        if (actualPromptTokens > 0 && estimatedPromptTokens > 0) {
            // Рассчитываем новый коэффициент на основе реальных данных
            val calculatedRatio = actualPromptTokens.toDouble() / estimatedPromptTokens.toDouble()

            // Плавная корректировка коэффициента (смешивание с текущим значением)
            val adjustedRatio =
                (currentTokenRatio * CURRENT_RATIO_WEIGHT) + (calculatedRatio * NEW_RATIO_WEIGHT)

            // Ограничиваем диапазон коэффициента
            val clampedRatio = adjustedRatio.coerceIn(MIN_RATIO, MAX_RATIO)

            currentTokenRatio = clampedRatio
            isAccurate = true // Устанавливаем флаг точности
        }
    }

    /**
     * Сбросить коэффициент к значению по умолчанию и сбросить флаг точности.
     */
    override fun resetRatio() {
        currentTokenRatio = DEFAULT_TOKEN_RATIO
        isAccurate = false
    }

    /**
     * Получить текущий коэффициент.
     */
    override fun getCurrentRatio(): Double = currentTokenRatio

    /**
     * Получить флаг, указывающий, был ли коэффициент скорректирован на основе реальных данных.
     */
    override fun isRatioAccurate(): Boolean = isAccurate
}
