package com.arny.aipromptmaster.presentation.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.presentation.databinding.ItemAssistantMessageBinding
import com.arny.aipromptmaster.presentation.databinding.ItemErrorBinding
import com.arny.aipromptmaster.presentation.databinding.ItemLoadingBinding
import com.arny.aipromptmaster.presentation.databinding.ItemUserMessageBinding

/**
 * Адаптер для чата с поддержкой разных типов сообщений
 * @param onMessageClickListener обработчик кликов по сообщениям
 */
class AiChatAdapter(
    private val onMessageClickListener: ((AiChatMessage) -> Unit)? = null
) : ListAdapter<AiChatMessage, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AiChatMessage>() {
            override fun areItemsTheSame(
                oldItem: AiChatMessage,
                newItem: AiChatMessage
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: AiChatMessage,
                newItem: AiChatMessage
            ): Boolean = oldItem == newItem

            override fun getChangePayload(
                oldItem: AiChatMessage,
                newItem: AiChatMessage
            ): Any? {
                return if (oldItem.content != newItem.content) {
                    Bundle().apply { putString("content", newItem.content) }
                } else {
                    super.getChangePayload(oldItem, newItem)
                }
            }
        }

        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_LOADING = 2
        private const val TYPE_ERROR = 3
        
        // Добавлено для логирования производительности
        private const val LOG_TAG = "ChatAdapterPerf"
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position).type) {
        is AiChatMessageType.USER -> TYPE_USER
        is AiChatMessageType.ASSISTANT -> TYPE_ASSISTANT
        is AiChatMessageType.LOADING -> TYPE_LOADING
        is AiChatMessageType.ERROR -> TYPE_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserMessageViewHolder(
                ItemUserMessageBinding.inflate(inflater, parent, false),
                onMessageClickListener
            )
            TYPE_ASSISTANT -> AssistantMessageViewHolder(
                ItemAssistantMessageBinding.inflate(inflater, parent, false),
                onMessageClickListener
            )
            TYPE_LOADING -> LoadingViewHolder(
                ItemLoadingBinding.inflate(inflater, parent, false)
            )
            TYPE_ERROR -> ErrorMessageViewHolder(
                ItemErrorBinding.inflate(inflater, parent, false),
                onMessageClickListener
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val startTime = System.currentTimeMillis()
        val item = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(item)
            is AssistantMessageViewHolder -> holder.bind(item)
            is LoadingViewHolder -> holder.bind(item)
            is ErrorMessageViewHolder -> holder.bind(item)
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 16) { // Больше 16ms - проблема для 60fps
            android.util.Log.w(LOG_TAG, "Slow binding at pos $position: ${duration}ms")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads[0] is Bundle) {
            val bundle = payloads[0] as Bundle
            when (holder) {
                is UserMessageViewHolder -> holder.updateContent(bundle.getString("content"))
                is AssistantMessageViewHolder -> holder.updateContent(bundle.getString("content"))
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class UserMessageViewHolder(
        private val binding: ItemUserMessageBinding,
        private val clickListener: ((AiChatMessage) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: AiChatMessage) {
            with(binding) {
                tvMessage.text = message.content
                root.setOnClickListener { clickListener?.invoke(message) }
            }
        }

        fun updateContent(newContent: String?) {
            binding.tvMessage.text = newContent
        }
    }

    class AssistantMessageViewHolder(
        private val binding: ItemAssistantMessageBinding,
        private val clickListener: ((AiChatMessage) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: AiChatMessage) {
            with(binding) {
                tvMessage.text = message.content
                root.setOnClickListener { clickListener?.invoke(message) }
            }
        }

        fun updateContent(newContent: String?) {
            binding.tvMessage.text = newContent
        }
    }

    class LoadingViewHolder(
        private val binding: ItemLoadingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: AiChatMessage) {
            with(binding) {
                progressBar.visibility = if (message.type == AiChatMessageType.LOADING) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }

    class ErrorMessageViewHolder(
        private val binding: ItemErrorBinding,
        private val clickListener: ((AiChatMessage) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: AiChatMessage) {
            with(binding) {
                tvError.text = message.content
                root.setOnClickListener { clickListener?.invoke(message) }
            }
        }
    }
}
