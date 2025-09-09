package com.arny.aipromptmaster.presentation.ui.models

import android.content.Context
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.presentation.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val llmIconsBaseUrl = "https://raw.githubusercontent.com/TypingMind/model-icons/main/icons"

// Данные из репозитория TypingMind
private val iconMappings = mapOf(
    "gpt-4" to "gpt-4.webp",
    "gpt-3.5" to "gpt-35.webp",
    "gemini" to "gemini.png",
    "llama" to "llama.png",
    "mistral" to "mistralai.png",
    "claude" to "claude.webp",
    "azure-openai" to "azureopenai.png",
    "bing" to "bing.svg",
    "pi" to "pi-logo-192.png",
    "perplexity" to "perplexityai.png",
    "openrouterai" to "openrouterai.png",
    "llava" to "llava.png",
    "gpt4all" to "gpt4all.png",
    "vicuna" to "vicuna.png",
    "falcon" to "falcon.png",
    "huggingface" to "huggingface.png",
    "openai" to "openai.svg",
    "open-assistant" to "openassistant.webp",
    "replit" to "replit.png",
    "phi-2" to "phi-2.png",
    "gemma" to "gemma.jpg",
    "qwen2" to "qwen2.png",
    "deepseek" to "deepseek.png",
    "aya" to "aya.svg",
    "orca-mini" to "orca-mini.png"
)

// Маппинг конкретных моделей на иконки
private val modelToIconMap = mapOf(
    // GPT-4 модели
    "gpt-4" to "gpt-4",
    "gpt-4-turbo" to "gpt-4",
    "gpt-4-32k" to "gpt-4",
    "gpt-4-1106-preview" to "gpt-4",
    "gpt-4-0125-preview" to "gpt-4",
    "gpt-4-vision-preview" to "gpt-4",
    "gpt-4-turbo-preview" to "gpt-4",

    // GPT-3.5 модели
    "gpt-3.5-turbo" to "gpt-3.5",
    "gpt-3.5-turbo-16k" to "gpt-3.5",
    "gpt-3.5-turbo-1106" to "gpt-3.5",
    "gpt-3.5-turbo-0125" to "gpt-3.5",

    // Gemini модели
    "gemini-1.5-pro-latest" to "gemini",
    "gemini-ultra" to "gemini",
    "gemini-pro" to "gemini",
    "gemini-pro-vision" to "gemini",

    // LLaMA модели
    "codellama-70b-instruct" to "llama",
    "codellama-34b-instruct" to "llama",
    "llama-3-8b-instruct:extended" to "llama",
    "llama-3-70b-instruct:nitro" to "llama",

    // Mistral модели
    "mixtral-8x22b" to "mistral",
    "mistral-7b-instruct" to "mistral",
    "mistral-large" to "mistral",
    "mistral-tiny" to "mistral",
    "mistral-small" to "mistral",
    "mistral-medium" to "mistral",

    // Claude модели
    "claude-3-opus-20240229" to "claude",
    "claude-3-sonnet-20240229" to "claude",
    "claude-3-haiku-20240307" to "claude",
    "claude-2.1" to "claude",
    "claude-2" to "claude",
    "claude-1" to "claude",
    "claude-instant-1" to "claude",
    "claude-instant-1.2" to "claude",

    // Другие модели
    "replit-code-v1-3b" to "replit",
    "phi-2" to "phi-2",
    "Qwen2-72B-Instruct2" to "qwen2",
    "Qwen2-72B" to "qwen2",
    "Qwen2-7B-Instruct" to "qwen2",
    "DeepSeek-V2" to "deepseek",
    "DeepSeek-Coder-V2" to "deepseek",
    "deepseek-llm" to "deepseek",
    "deepseek-coder" to "deepseek",
    "aya-23-8B" to "aya",
    "aya-23-35B" to "aya",
    "orca-mini" to "orca-mini"
)

private val ONE_THOUSAND = BigDecimal(1000)
private val ONE_MILLION = BigDecimal(1_000_000)

fun LlmModel.formatDescription(context: Context): String {
    return buildString {
        // Дата создания
        append(context.getString(R.string.created))
        append(" ")
        append(created.toReadableDate())

        // Контекст
        append("\n")
        append(context.getString(R.string.context))
        append(" ")
        append(contextLength.toCompactString())

        // Цены
        append("\n")
        append(formatPricingWithEmojis())
    }
}

private fun LlmModel.formatPricingWithEmojis(): String {
    return buildString {
        val promptPrice = (pricingPrompt * BigDecimal(1_000_000)).toCompactString()
        val completionPrice = (pricingCompletion * BigDecimal(1_000_000)).toCompactString()

        // 📥 - входящие (ваш запрос к модели)
        append("📥$")
        append(promptPrice)
        append("/")

        // 📤 - исходящие (ответ модели)
        append("📤$")
        append(completionPrice)
        append("/1M")

        pricingImage?.let { imagePrice ->
            if (imagePrice > BigDecimal.ZERO) {
                append(" | 🖼️$")
                append(imagePrice.toCompactString())
            }
        }
    }
}


fun Long.toReadableDate(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(this*1000))
}

/**
 * Форматирует BigDecimal в компактную строку с суффиксами 'K' (тысячи) или 'M' (миллионы).
 * - Если число >= 1,000,000, оно делится на 1,000,000, округляется до 2 знаков и добавляется 'M'.
 * - Если число >= 1,000, оно делится на 1,000, округляется до 2 знаков и добавляется 'K'.
 * - Если число < 1,000, оно округляется до 2 знаков, если есть дробная часть,
 *   или отображается как целое, если дробной части нет.
 *
 * @param scale Количество знаков после запятой для округления. По умолчанию 2.
 * @param roundingMode Режим округления. По умолчанию HALF_UP.
 * @return Отформатированная строка.
 */
fun BigDecimal.toCompactString(
    scale: Int = 2,
    roundingMode: RoundingMode = RoundingMode.HALF_UP
): String {
    // Используем when для проверки пороговых значений
    return when {
        // Сравниваем с миллионом. compareTo возвращает >= 0, если this >= ONE_MILLION
        this >= ONE_MILLION -> {
            val result = this.divide(ONE_MILLION, scale, roundingMode)
            // toPlainString() используется для предотвращения вывода в научной нотации (например, 1.2E+1)
            "${result.toPlainString()}M"
        }

        // Сравниваем с тысячей
        this >= ONE_THOUSAND -> {
            val result = this.divide(ONE_THOUSAND, scale, roundingMode)
            "${result.toPlainString()}K"
        }

        // Случай для чисел меньше 1000
        else -> {
            // Проверяем, является ли число целым.
            // stripTrailingZeros().scale() <= 0 - надежный способ это сделать.
            if (this.stripTrailingZeros().scale() <= 0) {
                this.toBigInteger().toString() // Если целое, выводим без .00
            } else {
                this.setScale(scale, roundingMode)
                    .toPlainString() // Если есть дробная часть, округляем
            }
        }
    }
}


private fun getModelIconUrl(modelId: String): String {
    // Убираем префикс провайдера и суффиксы OpenRouter
    val cleanModelId = cleanModelId(modelId)

    // Ищем точное совпадение в маппинге моделей
    val iconId = modelToIconMap[cleanModelId] ?: findIconByPattern(cleanModelId)

    // Получаем имя файла иконки
    val iconFileName = iconMappings[iconId] ?: "openai.svg"

    return "$llmIconsBaseUrl/$iconFileName"
}

private fun cleanModelId(modelId: String): String {
    // Убираем суффиксы OpenRouter
    val withoutSuffix = modelId.split(":")[0]

    // Убираем префикс провайдера (например, "openai/", "anthropic/")
    val parts = withoutSuffix.split("/")
    return if (parts.size > 1) parts[1] else withoutSuffix
}

private fun findIconByPattern(modelId: String): String {
    return when {
        modelId.startsWith("gpt-4") -> "gpt-4"
        modelId.startsWith("gpt-3.5") || modelId.startsWith("gpt-35") -> "gpt-3.5"
        modelId.startsWith("claude") -> "claude"
        modelId.startsWith("gemini") -> "gemini"
        modelId.startsWith("llama") || modelId.startsWith("codellama") -> "llama"
        modelId.startsWith("mistral") || modelId.startsWith("mixtral") -> "mistral"
        modelId.startsWith("deepseek") -> "deepseek"
        modelId.startsWith("qwen") -> "qwen2"
        modelId.startsWith("phi") -> "phi-2"
        modelId.startsWith("aya") -> "aya"
        modelId.startsWith("replit") -> "replit"
        modelId.startsWith("gemma") -> "gemma"
        modelId.startsWith("vicuna") -> "vicuna"
        modelId.startsWith("falcon") -> "falcon"
        modelId.startsWith("llava") -> "llava"
        modelId.startsWith("orca") -> "orca-mini"
        else -> "openai" // дефолтная иконка
    }
}

fun getModelIconUrlWithFallback(modelId: String, defaultIconId: String = "openai"): String {
    return try {
        getModelIconUrl(modelId)
    } catch (e: Exception) {
        val fallbackFile = iconMappings[defaultIconId] ?: "openai.svg"
        "$llmIconsBaseUrl/$fallbackFile"
    }
}

