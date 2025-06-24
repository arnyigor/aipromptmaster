package com.arny.aipromptmaster.presentation.ui.home

import android.text.method.LinkMovementMethod
import android.view.View
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemLlmmessageBinding
import com.xwray.groupie.viewbinding.BindableItem

class LLMMessageItem(private val message: String) : BindableItem<ItemLlmmessageBinding>() {
    override fun bind(viewBinding: ItemLlmmessageBinding, position: Int) {
        viewBinding.tvMessage.apply {
            text = message
            movementMethod = LinkMovementMethod.getInstance() // Для кликабельных ссылок
        }
    }

    override fun getLayout() = R.layout.item_llmmessage

    override fun initializeViewBinding(view: View) = ItemLlmmessageBinding.bind(view)
}