package com.arny.aipromptmaster.domain.models.strings

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.arny.aipromptmaster.domain.R

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


fun Throwable.toErrorHolder(
    @StringRes defaultRes: Int = R.string.system_error
): StringHolder = message?.takeUnless { it.isBlank() }
    ?.let { StringHolder.Text(it) }
    ?: StringHolder.Resource(defaultRes)