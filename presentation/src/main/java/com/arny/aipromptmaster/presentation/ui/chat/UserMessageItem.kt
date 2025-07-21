package com.arny.aipromptmaster.presentation.ui.chat

import android.view.View
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemUserMessageBinding
import com.xwray.groupie.viewbinding.BindableItem

class UserMessageItem(
    private val message: ChatMessage,
    private val onCopyClicked: (String) -> Unit = {}
) : BindableItem<ItemUserMessageBinding>() {

    override fun bind(viewBinding: ItemUserMessageBinding, position: Int) {
        viewBinding.tvMessage.text = message.content
        viewBinding.btnCopy.setOnClickListener {
            onCopyClicked(message.content)
        }
    }

    override fun getLayout() = R.layout.item_user_message

    override fun initializeViewBinding(view: View) = ItemUserMessageBinding.bind(view)

    override fun getId(): Long = message.id.hashCode().toLong()
}
