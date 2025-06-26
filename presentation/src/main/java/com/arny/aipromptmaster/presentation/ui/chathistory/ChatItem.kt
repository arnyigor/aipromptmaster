package com.arny.aipromptmaster.presentation.ui.chathistory

import android.view.View
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemChatBinding
import com.xwray.groupie.viewbinding.BindableItem
import java.text.SimpleDateFormat
import java.util.Locale

class ChatItem(
    private val chat: Chat,
    private val onClick: ((Chat) -> Unit)? = null
) : BindableItem<ItemChatBinding>() {

    override fun bind(viewBinding: ItemChatBinding, position: Int) {
        with(viewBinding) {
            chatName.text = chat.name
            lastMessage.text = chat.lastMessage
            try {
                timestamp.text = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                    .format(chat.timestamp)
            } catch (_: Exception) {
                timestamp.text = ""
            }
            root.setOnClickListener { onClick?.invoke(chat) }
        }
    }

    override fun getLayout() = R.layout.item_chat
    override fun initializeViewBinding(view: View) = ItemChatBinding.bind(view)
}
