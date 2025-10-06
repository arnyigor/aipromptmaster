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
    private val isStreaming: Boolean = false
) : BindableItem<ItemAiMessageBinding>() {

    companion object {
        const val PAYLOAD_TEXT_UPDATE = "text_update"
        const val PAYLOAD_STREAMING_STATE = "streaming_state"
    }

    override fun bind(viewBinding: ItemAiMessageBinding, position: Int) = with(viewBinding){
        val emptyContent = message.content.isEmpty()

        // Показываем блок с названием модели, если оно предоставлено
        val hasModelName = !modelName.isNullOrBlank()
        tvModelName.isVisible = hasModelName
        if (hasModelName) {
            tvModelName.text = modelName
        } else {
            tvModelName.text = root.context.getString(R.string.ai_assystent)
        }

        // Показываем индикатор печати во время стриминга
        tvPlaceholder.isVisible = emptyContent && !isStreaming
        tvMessage.isVisible = !emptyContent || isStreaming
        typingIndicator.isVisible = isStreaming

        if (!emptyContent || isStreaming) {
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

    override fun bind(
        viewBinding: ItemAiMessageBinding,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_TEXT_UPDATE)) {
            // Обновить только текст без перерисовки всего ViewHolder
            viewBinding.tvMessage.post {
                viewBinding.tvMessage.text = markwon.toMarkdown(message.content)
            }
        } else if (payloads.contains(PAYLOAD_STREAMING_STATE)) {
            // Обновить только состояние стриминга
            val emptyContent = message.content.isEmpty()
            viewBinding.tvPlaceholder.isVisible = emptyContent && !isStreaming
            viewBinding.tvMessage.isVisible = !emptyContent || isStreaming
        } else {
            super.bind(viewBinding, position, payloads)
        }
    }

    override fun getLayout() = R.layout.item_ai_message

    override fun initializeViewBinding(view: View) = ItemAiMessageBinding.bind(view)

    override fun getId(): Long = message.id.hashCode().toLong()

    override fun isSameAs(other: com.xwray.groupie.Item<*>): Boolean {
        return other is AiMessageItem && other.message.id == message.id
    }

    override fun hasSameContentAs(other: com.xwray.groupie.Item<*>): Boolean {
        return other is AiMessageItem &&
               other.message.content == message.content &&
               other.isStreaming == isStreaming
    }
}
