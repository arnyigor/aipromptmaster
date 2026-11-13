package com.arny.aiprompts.platform

import com.arny.aiprompts.domain.interactors.AttachmentInput
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import javax.swing.SwingUtilities

actual fun pickChatAttachments(onPicked: (List<AttachmentInput>) -> Unit, onError: (String) -> Unit) {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, "Прикрепить файлы к сообщению", FileDialog.LOAD)
        try {
            dialog.isMultipleMode = true
            dialog.isVisible = true
            if (dialog.files.isNotEmpty()) onPicked(dialog.files.map { file ->
                AttachmentInput(file.absolutePath, file.name, Files.probeContentType(file.toPath())
                    ?: if (file.extension.lowercase() in listOf("md", "kt", "kts", "py", "js", "ts", "txt", "csv", "yaml", "yml")) "text/plain" else null)
            })
        } catch (e: Exception) { onError("Не удалось открыть файлы: ${e.message}") }
        finally { dialog.dispose() }
    }
}
