package com.arny.aipromptmaster.presentation.ui.chat

import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.isVisible
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemAiMessageBinding
import com.xwray.groupie.viewbinding.BindableItem
import io.noties.markwon.Markwon

class AiMessageItem(
    private val markwon: Markwon,
    private val message: ChatMessage,
    private val modelName: String? = null,
    private val onCopyClicked: (String) -> Unit,
) : BindableItem<ItemAiMessageBinding>() {

    override fun bind(viewBinding: ItemAiMessageBinding, position: Int) = with(viewBinding){
        val emptyContent = message.content.isEmpty()

        // Показываем блок с названием модели, если оно предоставлено
        val hasModelName = !modelName.isNullOrBlank()
        tvModelName.isVisible = hasModelName
        if (hasModelName) {
            tvModelName.text = modelName
        }else{
            tvModelName.text = root.context.getString(R.string.ai_assystent)
        }

        tvPlaceholder.isVisible = emptyContent
        btnCopy.isVisible = !emptyContent
        tvMessage.isVisible = !emptyContent

        if (!emptyContent) {
            // Используем построчечное обновление для более плавного стриминга
            tvMessage.post {
                tvMessage.text = markwon.toMarkdown(message.content)
                tvMessage.movementMethod = LinkMovementMethod.getInstance()
            }
        }

        btnCopy.setOnClickListener {
            onCopyClicked(message.content)
        }
    }

    override fun getLayout() = R.layout.item_ai_message

    override fun initializeViewBinding(view: View) = ItemAiMessageBinding.bind(view)

    override fun getId(): Long = message.id.hashCode().toLong()
}
