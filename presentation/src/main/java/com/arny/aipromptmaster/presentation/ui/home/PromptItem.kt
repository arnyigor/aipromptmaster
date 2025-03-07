package com.arny.aipromptmaster.presentation.ui.home

import android.view.View
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemPromptBinding
import com.xwray.groupie.viewbinding.BindableItem

class PromptItem(
    private val prompt: Prompt
) : BindableItem<ItemPromptBinding>() {
    override fun bind(viewBinding: ItemPromptBinding, position: Int) = with(viewBinding) {
        tvPromptText.text = prompt.title
        tvModel.text = prompt.aiModel
    }

    override fun getLayout(): Int = R.layout.item_prompt

    override fun initializeViewBinding(view: View): ItemPromptBinding {
        return ItemPromptBinding.bind(view)
    }

}
