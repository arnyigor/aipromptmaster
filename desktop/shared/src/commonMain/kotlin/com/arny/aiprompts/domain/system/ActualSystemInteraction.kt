// в desktopMain/com/arny/aiprompts/domain/system/ActualSystemInteraction.kt
package com.arny.aiprompts.domain.system

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import javax.swing.ImageIcon

// Имплементируем наш общий интерфейс
class ActualSystemInteraction : SystemInteraction {

    // Лениво инициализируем иконку один раз
    private val trayIcon: TrayIcon? by lazy {
        // Проверяем, что SystemTray вообще поддерживается
        if (!SystemTray.isSupported()) {
            println("SystemTray не поддерживается на данной платформе.")
            return@lazy null
        }

        // Попытка загрузить иконку. 
        // Важно: иконка должна быть в classpath, например в resources.
        val imageURL = javaClass.classLoader.getResource("icon.png")
        if (imageURL == null) {
            println("Иконка 'icon.png' не найдена в ресурсах.")
            // Создаем пустую иконку, чтобы TrayIcon не упал
            TrayIcon(Toolkit.getDefaultToolkit().createImage(""))
        } else {
            TrayIcon(ImageIcon(imageURL, "AI Prompts").image)
        }
    }

    override fun showNotification(title: String, message: String) {
        val icon = trayIcon ?: return // Если иконка не создалась, выходим

        try {
            val tray = SystemTray.getSystemTray()
            // Удаляем предыдущую иконку, если она была, чтобы избежать дубликатов
            if (icon in tray.trayIcons) {
                tray.remove(icon)
            }
            tray.add(icon)
            icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
        } catch (e: Exception) {
            println("Ошибка при показе уведомления: ${e.message}")
            e.printStackTrace()
        }
    }
}
