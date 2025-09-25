package com.arny.aiprompts.domain.strings


import androidx.compose.runtime.Composable
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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


@Composable
fun StringHolder.asString(): String {
    return when (this) {
        is StringHolder.Resource -> stringResource(id = id)
        is StringHolder.Text -> value ?: ""
        is StringHolder.Formatted -> stringResource(id = id, formatArgs = formatArgs.toTypedArray())
        is StringHolder.Plural -> pluralStringResource(
            id = id,
            count = quantity,
            formatArgs = formatArgs.toTypedArray()
        )
    }
}