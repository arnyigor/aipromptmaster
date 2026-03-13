package com.arny.aipromptmaster.data.mappers

import android.util.Log
import com.arny.aipromptmaster.data.db.entities.ModelEntity
import com.arny.aipromptmaster.data.models.ModelDTO
import com.arny.aipromptmaster.domain.models.LlmModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.util.Locale

/* --------------------------------------------------------------------------- */
/*  ── Вспомогательные расширения ───────────────────────────────────────────── */
/* --------------------------------------------------------------------------- */

private fun String?.toBigDecimalOrZero(): BigDecimal =
    try {
        if (this.isNullOrBlank()) BigDecimal.ZERO else BigDecimal(this)
    } catch (_: Exception) {
        BigDecimal.ZERO
    }

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (_: Exception) {
        null
    }

// формат “X / 1M” (или “Free”, «N/A»)
// Вынесено в отдельное расширение – так удобно использовать из любой части кода
private fun BigDecimal?.formatPricePerMillion(): String = when {
    this == null -> "N/A"
    this == BigDecimal.ZERO -> "Free"
    else -> {
        val decimal = this * BigDecimal(1000000)
        "$${String.format(Locale.getDefault(), "%.3f", decimal)}"
    }
}

// преобразуем целое число токенов в строку вида «128k»
// Используется только в `Entity → Domain`
fun Int.displayContextSize(): String =
    (this.toBigDecimal() / BigDecimal(1000)).toPlainString().let { "$it" } + "k"

/* --------------------------------------------------------------------------- */
/*  ── DTO   →   Entity ────────────────────────────────────────          */
/* --------------------------------------------------------------------------- */

/**
 * Конвертация ответа сервера (`ModelDTO`) в строку БД (`ModelEntity`).
 */
fun ModelDTO.toEntity(): ModelEntity {
    val dto = this

    /* ---------- цены ---------------------------------------- */
    val promptPrice = dto.pricing.prompt.toBigDecimalOrZero()
    val completionPrice = dto.pricing.completion.toBigDecimalOrZero()

    // Модель считается «Free» только если обе цены == 0
    val isFree = promptPrice <= BigDecimal.ZERO && completionPrice <= BigDecimal.ZERO

    /* ---------- модальности --------------------------------- */
    val inputJson = Json.encodeToString(dto.architecture.inputModalities)
    val outputJson = Json.encodeToString(dto.architecture.outputModalities)

    // Мультимодальная, если в списках есть «image»
    val isMultimodal = dto.architecture.inputModalities.contains("image") ||
            dto.architecture.outputModalities.contains("image")

    return ModelEntity(
        id = dto.id,
        name = dto.name,
        description = dto.description,
        contextLength = dto.contextLength,
        // Храним цены как строку – SQLite не потеряет точность
        pricingPrompt = dto.pricing.prompt,
        pricingCompletion = dto.pricing.completion,
        isMultimodal = isMultimodal,
        pricingImage = dto.pricing.image,
        isFree = isFree,
        lastUpdated = System.currentTimeMillis(),
        inputModalities = inputJson,
        outputModalities = outputJson
    )
}

/* --------------------------------------------------------------------------- */
/*  ── Entity → Domain ───────────────────────────────────────           */
/* --------------------------------------------------------------------------- */

/**
 * Конвертация строки БД (`ModelEntity`) в доменную модель (`LlmModel`),
 * которую использует UI.
 */
fun ModelEntity.toDomain(): LlmModel = with(this) {
    fun getList(json: String) =
        runCatching { Json.decodeFromString<List<String>>(json) }
            .getOrElse { emptyList() }

    LlmModel(
        id = id,
        name = name,
        description = description,

        // Храним timestamp как «created»
        created = lastUpdated,

        // Показываем размер контекста в виде “128k”
        contextLength = contextLength.displayContextSize(),

        // Форматируем цены
        pricingPrompt = pricingPrompt.toBigDecimal().formatPricePerMillion(),
        pricingCompletion = pricingCompletion.toBigDecimal().formatPricePerMillion(),
        pricingImage = pricingImage?.toBigDecimalOrNull()?.formatPricePerMillion(),
        inputModalities = getList(inputModalities),
        outputModalities = getList(outputModalities),
        isSelected = isSelected,
        isFavorite = isFavorite
    )
}


/**
 * Преобразует ответ сервера (`ModelDTO`) в доменную модель,
 * которую использует UI.
 *
 * Что изменено:
 * 1. Размер контекста теперь форматируется сразу как строка «128k».
 * 2. Цены конвертируются из `String` → `BigDecimal` и затем
 *    приводятся к пользовательскому формату (Free / $X.YY).
 * 3. Добавлена безопасная обработка возможных `null`‑значений.
 */
fun ModelDTO.toDomain(): LlmModel = with(this) {
    // ---------- Цены ----------
    val promptPrice = pricing.prompt.toBigDecimalOrZero()
    val completionPrice = pricing.completion.toBigDecimalOrZero()
    val imagePrice = pricing.image.toBigDecimalOrNull()

    LlmModel(
        id = id,
        name = name,
        description = description,
        isSelected = false,
        // Храним строку вида «128k»
        contextLength = contextLength.displayContextSize(),
        created = created,
        inputModalities = architecture.inputModalities,
        outputModalities = architecture.outputModalities,
        pricingPrompt = promptPrice.formatPricePerMillion(),
        pricingCompletion = completionPrice.formatPricePerMillion(),
        // image‑цена может быть null → “N/A”
        pricingImage = imagePrice?.formatPricePerMillion()
    )
}

