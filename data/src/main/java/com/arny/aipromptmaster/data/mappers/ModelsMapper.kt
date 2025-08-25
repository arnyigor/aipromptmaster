package com.arny.aipromptmaster.data.mappers

import com.arny.aipromptmaster.data.models.ApiError
import com.arny.aipromptmaster.data.models.ModelDTO
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import java.math.BigDecimal


fun ModelDTO.toDomain(): LlmModel = LlmModel(
    id = id,
    name = name,
    description = description,
    isSelected = false,
    contextLength = BigDecimal(contextLength),
    created = created,
    inputModalities = architecture.inputModalities,
    outputModalities = architecture.outputModalities,
    pricingPrompt = BigDecimal(pricing.prompt),
    pricingCompletion = BigDecimal(pricing.completion),
    pricingImage = BigDecimal(pricing.image)
)

// Функция-расширение для чистой конвертации
fun ApiError.toDomainError(): DomainError {
    return when (code) {
        400 -> DomainError.Api(code, "Неверный запрос", message)
        401 -> DomainError.Api(code, "Ошибка авторизации", "Проверьте API ключ")
        429 -> DomainError.Api(
            code = code,
            stringHolder = StringHolder.Text("Превышен лимит запросов"),
            detailedMessage = metadata?.raw ?: message
        )
        500 -> DomainError.Api(code, "Ошибка сервера", message)
        else -> DomainError.Api(code, "Ошибка API (Код: $code)", message)
    }
}