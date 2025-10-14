package com.arny.aipromptmaster.domain.models

/**
 * Результат оценки токенов.
 * @property estimatedTokens Приблизительное количество токенов.
 * @property isAccurate Был ли коэффициент корректирован на основе реальных данных.
 */
data class TokenEstimationResult(
    val estimatedTokens: Int,
    val isAccurate: Boolean
)