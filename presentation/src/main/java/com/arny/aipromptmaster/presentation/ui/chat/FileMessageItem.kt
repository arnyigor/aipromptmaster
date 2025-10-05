package com.arny.aipromptmaster.presentation.ui.chat

import android.view.View
import androidx.core.view.isVisible
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.utils.FileUtils
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemFileMessageBinding
import com.xwray.groupie.viewbinding.BindableItem

class FileMessageItem(
    private val message: ChatMessage,
    private val onViewFile: (FileAttachmentMetadata) -> Unit,
) : BindableItem<ItemFileMessageBinding>() {

    override fun bind(viewBinding: ItemFileMessageBinding, position: Int) {
        val fileAttachment = message.fileAttachment ?: return

        with(viewBinding) {
            // Устанавливаем иконку файла
            ivFileIcon.setImageResource(R.drawable.outline_attach_file_24)

            // Показываем информацию о файле
            tvFileName.text = fileAttachment.fileName
            tvFileSize.text = FileUtils.formatFileSize(fileAttachment.fileSize)
            tvFileType.text = fileAttachment.fileExtension.uppercase()

            // Показываем превью содержимого (первые несколько строк)
            val preview = fileAttachment.preview
            tvFilePreview.text = preview
            tvFilePreview.isVisible = preview.isNotEmpty()

            // Обработчик клика для просмотра полного файла
            root.setOnClickListener {
                onViewFile(fileAttachment)
            }

            // Обработчик долгого клика для копирования содержимого
            root.setOnLongClickListener {
                // Здесь можно добавить логику копирования содержимого файла
                true
            }
        }
    }

    private fun generatePreview(content: String): String {
        val lines = content.lines()
        return when {
            lines.size <= 3 -> content
            else -> lines.take(3).joinToString("\n") + "\n..."
        }
    }

    override fun getLayout() = R.layout.item_file_message

    override fun initializeViewBinding(view: View) = ItemFileMessageBinding.bind(view)

    override fun getId(): Long = message.id.hashCode().toLong()
}