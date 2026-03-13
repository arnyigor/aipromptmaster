package com.arny.aipromptmaster.data.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.services.ShareService
import java.io.File
import java.io.IOException

class ShareServiceImpl(private val context: Context) : ShareService {

    override fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Запускаем chooser, чтобы пользователь выбрал приложение
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_via))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override  fun copyToClipboard(content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", content)
        clipboard.setPrimaryClip(clip)
    }

    override fun exportFile(fileName: String, content: String) {
        try {
            // 1️⃣ Создаём временный файл во внутреннем кэше
            val cacheDir = context.cacheDir
            val file = File(cacheDir, fileName)

            // 2️⃣ Записываем контент в файл
            file.writeText(content)

            // 3️⃣ Получаем безопасный Uri через FileProvider
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // 4️⃣ Формируем Intent для отправки файла
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"          // MIME‑тип можно менять при необходимости
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

            // 5️⃣ Запускаем chooser
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.share_via))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

        } catch (e: IOException) {
            e.printStackTrace()
            // Можно добавить обратную связь пользователю через Snackbar/Toast
        }
    }
}