package com.arny.aiprompts.domain.model

data class ReferencePrompt(
    val id: String,
    val category: String,
    val prompt: String
)

data class ClassificationResult(
    val predictedCategory: String,
    val confidence: Double,
    val neighbors: List<NeighborInfo>
)

data class NeighborInfo(
    val prompt: String,
    val category: String,
    val similarity: Double
)

data class PromptMatchResult(
    val originalPrompt: String,
    val similarityScore: Double,
    val isPotentialDuplicate: Boolean
)