package com.arny.aipromptmaster.presentation.ui.home

import android.view.View
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemUserMessageBinding
import com.xwray.groupie.viewbinding.BindableItem

class UserMessageItem(private val message: String) : BindableItem<ItemUserMessageBinding>() {
    override fun bind(viewBinding: ItemUserMessageBinding, position: Int) {
        viewBinding.tvMessage.text = message
    }

    override fun getLayout() = R.layout.item_user_message

    override fun initializeViewBinding(view: View) = ItemUserMessageBinding.bind(view)
}