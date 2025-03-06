package com.arny.aipromptmaster.domain

sealed class PromptBlock {
    abstract val id: String

    data class TextBlock(
        override val id: String,
        var content: String
    ) : PromptBlock()

    data class ParameterBlock(
        override val id: String,
        var key: String,
        var value: String,
        val type: ParamType // TEXT, NUMBER, RATIO
    ) : PromptBlock()

    enum class ParamType { TEXT, NUMBER, RATIO }
}