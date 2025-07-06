package com.arny.aipromptmaster.presentation.utils

import android.content.res.ColorStateList
import android.graphics.Color
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors

fun ChipGroup.addTags(tags: List<String>) {
    removeAllViews()
    tags.forEach { tag ->
        addView(Chip(context).apply {
            text = tag
            isCheckable = false
        })
    }
}


fun ChipGroup.addFilterChips(
    tags: List<String>,
    onChipClicked: (tag: String, isSelected: Boolean) -> Unit
) {
    removeAllViews()

    tags.forEach { tag ->
        // Используем стандартный стиль Filter
        val chip = Chip(context, null, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Filter)
        chip.text = tag
        chip.isCheckable = true

        // Получаем контрастный цвет из атрибута темы
        val colorOnPrimary = MaterialColors.getColor(context,  com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
        val checkedIconTint = ColorStateList.valueOf(colorOnPrimary)
        chip.setTextColor(checkedIconTint)
        chip.chipIconTint = checkedIconTint
        chip.setOnClickListener {
            onChipClicked(tag, chip.isSelected)
        }
        addView(chip)
    }
}