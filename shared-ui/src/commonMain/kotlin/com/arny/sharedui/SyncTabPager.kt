package com.arny.sharedui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** Route owners remain authoritative; only a settled user swipe selects a new route. */
@Composable
fun SyncTabPager(pager: PagerState, selectedPage: Int, onSwipeSettled: (Int) -> Unit) {
    val isDragged by pager.interactionSource.collectIsDraggedAsState()
    val onSettled by rememberUpdatedState(onSwipeSettled)
    val currentSelection by rememberUpdatedState(selectedPage)
    LaunchedEffect(pager) {
        snapshotFlow { isDragged }.filter { it }.collect {
            val selectionAtDragStart = currentSelection
            snapshotFlow { !pager.isScrollInProgress }.first { it }
            if (currentSelection == selectionAtDragStart) onSettled(pager.settledPage)
        }
    }
    LaunchedEffect(pager, selectedPage) {
        if (selectedPage in 0 until pager.pageCount && selectedPage != pager.currentPage) {
            pager.scrollToPage(selectedPage)
        }
    }
}
