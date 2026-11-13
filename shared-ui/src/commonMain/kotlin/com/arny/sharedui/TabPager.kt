package com.arny.sharedui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Common gesture/layout policy; each platform keeps its existing route/state owner. */
@Composable
fun TabPager(state: PagerState, swipeEnabled: Boolean, content: @Composable (Int) -> Unit) {
    HorizontalPager(state = state, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1,
        userScrollEnabled = LocalWindowLayout.current == WindowLayout.Compact && swipeEnabled,
        pageContent = { page -> content(page) })
}
