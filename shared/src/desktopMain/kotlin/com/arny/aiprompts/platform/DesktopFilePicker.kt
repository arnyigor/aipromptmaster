package com.arny.aiprompts.platform

import com.arny.aiprompts.data.model.PlatformFile
import com.arny.aiprompts.data.model.createPlatformFile
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Desktop реализация FilePicker.
 * Использует AWT FileDialog для выбора файлов.
 */
class DesktopFilePicker(
    private val onFilePicked: (PlatformFile) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) {
    fun launch() {
        showFileDialog(multiple = false)
    }

    fun launchMultiple() {
        showFileDialog(multiple = true)
    }

    private fun showFileDialog(multiple: Boolean) {
        try {
            // Устанавливаем Look & Feel для нативного вида
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (e: Exception) {
                // Ignore - будет использоваться default L&F
            }

            SwingUtilities.invokeLater {
                val dialog = FileDialog(
                    null as Frame?,
                    "Выберите файл",
                    FileDialog.LOAD
                ).apply {
                    isMultipleMode = multiple
                }

                dialog.isVisible = true

                val files = dialog.files

                if (files.isNotEmpty()) {
                    files.forEach { file ->
                        try {
                            val platformFile = createPlatformFile(file.absolutePath)
                            onFilePicked(platformFile)
                        } catch (e: Exception) {
                            onError(e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            onError(e)
        }
    }
}
