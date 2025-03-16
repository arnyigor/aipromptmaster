package com.arny.aipromptmaster.presentation.utils

import android.content.Context
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