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
    private val onClick: (Chat) -> Unit,
    private val onLongClick: (Chat) -> Unit
) : BindableItem<ItemChatBinding>() {

    override fun bind(viewBinding: ItemChatBinding, position: Int) {
        with(viewBinding) {
            chatName.text = chat.name
            // Используем оператор безопасного вызова и элвис, как мы решили ранее
            lastMessage.text = chat.lastMessage ?: root.context.getString(R.string.no_messages)
            try {
                timestamp.text = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                    .format(chat.timestamp)
            } catch (_: Exception) {
                timestamp.text = ""
            }
            root.setOnClickListener { onClick.invoke(chat) }
            // УСТАНАВЛИВАЕМ СЛУШАТЕЛЬ ДОЛГОГО НАЖАТИЯ
            root.setOnLongClickListener {
                onLongClick.invoke(chat)
                true // Возвращаем true, чтобы показать, что событие обработано
            }
        }
    }

    override fun getLayout() = R.layout.item_chat
    override fun initializeViewBinding(view: View) = ItemChatBinding.bind(view)
}