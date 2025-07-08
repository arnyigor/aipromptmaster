package com.arny.aipromptmaster.presentation.utils

import android.content.Context
import android.view.View
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

fun ChipGroup.addTags(context: Context, tags: List<String>) {
    removeAllViews()
    tags.forEach { tag ->
        addView(Chip(context).apply {
            text = tag
            isCheckable = false
        })
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