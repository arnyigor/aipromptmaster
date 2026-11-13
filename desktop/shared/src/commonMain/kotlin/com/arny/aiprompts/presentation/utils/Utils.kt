package com.arny.aiprompts.presentation.utils

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Актуальная реализация для Desktop.
 * Использует AWT Toolkit для копирования текста.
 */
fun copyToClipboard(text: String) {
    val selection = StringSelection(text)
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(selection, selection)
}