package com.arny.aipromptmaster.presentation.ui.chat

import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString
import java.io.File

data class AiChatMessage(
    val id: String,
    val content: String = "",
    val file: File? = null,
    val error: IWrappedString? = null,
    val type: AiChatMessageType
)