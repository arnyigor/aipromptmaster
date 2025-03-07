package com.arny.aipromptmaster.presentation.utils.strings

import android.content.Context

class SimpleString(val string: String?) : IWrappedString {
    override fun toString(context: Context): String? = string
}