package com.arny.aiprompts.platform

import com.arny.aiprompts.data.model.PlatformFile

/**
 * Кроссплатформенный интерфейс для выбора файлов.
 * 
 * Использование:
 * - Desktop: Создайте экземпляр DesktopFilePicker напрямую
 * - Android: Используйте @Composable функции rememberFilePicker
 */
interface FilePicker {
    fun launch()
    fun launchMultiple()
}
