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
    private val onCopyClicked: (String) -> Unit,
) : BindableItem<ItemAiMessageBinding>() {

    override fun bind(viewBinding: ItemAiMessageBinding, position: Int) {
        val emptyContent = message.content.isEmpty()
        viewBinding.tvPlaceholder.isVisible = emptyContent
        viewBinding.btnCopy.isVisible = !emptyContent
        viewBinding.tvMessage.isVisible = !emptyContent
        if (!emptyContent) {
            viewBinding.tvMessage.text = markwon.toMarkdown(message.content)
            viewBinding.tvMessage.movementMethod = LinkMovementMethod.getInstance()
        }
        viewBinding.btnCopy.setOnClickListener {
            onCopyClicked(message.content)
        }
    }

    override fun getLayout() = R.layout.item_ai_message

    override fun initializeViewBinding(view: View) = ItemAiMessageBinding.bind(view)

    override fun getId(): Long = message.id.hashCode().toLong()
}
