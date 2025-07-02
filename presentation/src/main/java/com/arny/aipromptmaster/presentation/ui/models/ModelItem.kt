package com.arny.aipromptmaster.presentation.ui.models

import android.view.View
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemModelBinding
import com.xwray.groupie.viewbinding.BindableItem

class ModelItem(
    private val model: LLMModel,
    private val onItemClick: (model: LLMModel) -> Unit
) : BindableItem<ItemModelBinding>() {
    override fun bind(viewBinding: ItemModelBinding, position: Int) {
        viewBinding.tvModelName.text = model.name
        viewBinding.tvModelProvider.text = model.description
        viewBinding.switchModelActive.isChecked = model.isSelected
        viewBinding.ivProviderIcon.setImageResource(R.drawable.ic_deepseek_model)
        viewBinding.clContainer.setOnClickListener {
            if (!model.isSelected) {
                viewBinding.switchModelActive.isChecked = true
            }
            onItemClick(model)
        }
    }

    override fun getLayout() = R.layout.item_model

    override fun initializeViewBinding(view: View) = ItemModelBinding.bind(view)
}