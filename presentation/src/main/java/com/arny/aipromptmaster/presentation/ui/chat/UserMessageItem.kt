package com.arny.aipromptmaster.presentation.ui.chat

import android.text.method.LinkMovementMethod
import android.view.View
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemUserMessageBinding
import com.xwray.groupie.viewbinding.BindableItem
import io.noties.markwon.Markwon

class UserMessageItem(
    private val markwon: Markwon,
    private val message: ChatMessage,
    private val onCopyClicked: (String) -> Unit = {},
    private val onRegenerateClicked: (String) -> Unit = {}
) : BindableItem<ItemUserMessageBinding>() {

    override fun bind(viewBinding: ItemUserMessageBinding, position: Int) {
        // Используем Markwon для рендеринга markdown в сообщении пользователя
        viewBinding.tvMessage.post {
            viewBinding.tvMessage.text = markwon.toMarkdown(message.content)
            viewBinding.tvMessage.movementMethod = LinkMovementMethod.getInstance()
        }

        viewBinding.btnCopy.setOnClickListener {
            onCopyClicked(message.content)
        }
        viewBinding.btnRegenerate.setOnClickListener {
            onRegenerateClicked(message.content)
        }
    }

    override fun getLayout() = R.layout.item_user_message

    override fun initializeViewBinding(view: View) = ItemUserMessageBinding.bind(view)

    override fun getId(): Long = message.id.hashCode().toLong()
}
