package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action

// Pure grid index math, shared by both scroll directions: slots are linear
// indices row-major; NAV moves by one slot/row; PAGE (L1/R1) flips a whole
// page (columns x visible rows) in both "vertical" and "horizontal" modes.
class GridNavigation(
    itemCount: Int,
    columns: Int,
    visibleRows: Int,
) {
    private val safeCount = itemCount.coerceAtLeast(0)
    private val safeColumns = columns.coerceAtLeast(1)
    private val safeVisibleRows = visibleRows.coerceAtLeast(1)
    private val pageSize = safeColumns * safeVisibleRows

    fun move(index: Int, action: Action): Int {
        if (safeCount <= 0) return 0
        val maxIndex = safeCount - 1
        val current = index.coerceIn(0, maxIndex)
        val target = when (action) {
            Action.NAV_LEFT -> current - 1
            Action.NAV_RIGHT -> current + 1
            Action.NAV_UP -> current - safeColumns
            Action.NAV_DOWN -> current + safeColumns
            Action.PAGE_PREV -> current - pageSize
            Action.PAGE_NEXT -> current + pageSize
            else -> current
        }
        return target.coerceIn(0, maxIndex)
    }

    /** Zero-based page index containing [index]. */
    fun pageOf(index: Int): Int {
        if (safeCount <= 0) return 0
        return index.coerceIn(0, safeCount - 1) / pageSize
    }

    /** Total number of pages (at least 1). */
    fun pageCount(): Int =
        if (safeCount <= 0) 1 else (safeCount + pageSize - 1) / pageSize
}
