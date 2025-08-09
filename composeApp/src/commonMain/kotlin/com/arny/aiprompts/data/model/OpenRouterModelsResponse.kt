package com.arny.aiprompts.data.model

import kotlinx.serialization.Serializable

// Необходимые data-классы для парсинга JSON
@Serializable
data class OpenRouterModelsResponse(val data: List<ModelDTO>)