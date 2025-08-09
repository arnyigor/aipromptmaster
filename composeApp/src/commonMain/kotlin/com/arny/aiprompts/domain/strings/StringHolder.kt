package com.arny.aiprompts.domain.strings

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

sealed interface StringHolder {

    @JvmInline
    value class Resource(@StringRes val id: Int) : StringHolder

    @JvmInline
    value class Text(val value: String?) : StringHolder

    data class Formatted(
        @StringRes val id: Int,
        val formatArgs: List<Any>
    ) : StringHolder

    data class Plural(
        @PluralsRes val id: Int,
        val quantity: Int,
        val formatArgs: List<Any> = emptyList()
    ) : StringHolder
}
