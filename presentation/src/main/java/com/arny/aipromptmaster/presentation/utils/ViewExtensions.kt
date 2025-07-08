package com.arny.aipromptmaster.presentation.utils

import android.view.View
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

/**
 * Возвращает текст из единственного выбранного Chip в ChipGroup.
 * Если выбрано несколько или ни одного, возвращает null.
 * Подходит для ChipGroup с singleSelection="true".
 *
 * @return Текст выбранного Chip или null.
 */
fun ChipGroup.getSingleSelectedChipText(): String? {
    // checkedChipId возвращает ID выбранного чипа или View.NO_ID, если ничего не выбрано.
    val checkedChipId = this.checkedChipId
    return if (checkedChipId != View.NO_ID) {
        // Находим View по ID и кастуем к Chip, затем безопасно берем текст.
        findViewById<Chip>(checkedChipId)?.text?.toString()
    } else {
        null
    }
}

/**
 * Возвращает список текстов из всех выбранных Chip в ChipGroup.
 * Подходит для ChipGroup с singleSelection="false".
 *
 * @return Список строк с текстами выбранных Chip. Будет пустым, если ничего не выбрано.
 */
fun ChipGroup.getMultiSelectedChipTexts(): List<String> {
    // checkedChipIds возвращает список ID всех выбранных чипов.
    // Если ничего не выбрано, список будет пустым.
    return this.checkedChipIds.mapNotNull { chipId ->
        // mapNotNull - идеальный оператор здесь. Он выполняет преобразование
        // и автоматически отфильтровывает все null результаты.
        // Если findViewById вернет null (что маловероятно, но возможно),
        // этот элемент просто не попадет в итоговый список.
        findViewById<Chip>(chipId)?.text?.toString()
    }
}