package com.visorcraft.ghostgalleon.ui.deck

/**
 * Minimal ensure-visible scroll arithmetic for the grid and folder modal.
 * Pure so the decision (scroll up just enough / down just enough / not at all /
 * center row cleanly) stays host-testable; GridDeck feeds it live measurements
 * taken from the GridView itself, never cached offsets.
 */
object EnsureVisibleScroll {

    /**
     * Signed pixel delta for AbsListView.scrollListBy that brings
     * [itemTop, itemBottom] (content coordinates) fully inside the viewport
     * [viewportTop, viewportBottom] (content coordinates): negative scrolls
     * up just enough, positive scrolls down just enough, 0 means the item
     * is already fully visible and the grid must not move.
     */
    fun delta(itemTop: Int, itemBottom: Int, viewportTop: Int, viewportBottom: Int): Int {
        if (viewportBottom <= viewportTop) return 0
        return when {
            itemTop < viewportTop -> itemTop - viewportTop
            itemBottom > viewportBottom -> itemBottom - viewportBottom
            else -> 0
        }
    }

    /**
     * Clamped vertical scrollY that centers `[itemTop, itemBottom]` inside a
     * scrollable viewport of [viewportHeight] over [contentHeight] pixels.
     */
    fun centeredScrollY(
        itemTop: Int,
        itemBottom: Int,
        viewportHeight: Int,
        contentHeight: Int,
    ): Int {
        if (viewportHeight <= 0 || contentHeight <= viewportHeight) return 0
        val itemHeight = (itemBottom - itemTop).coerceAtLeast(0)
        val ideal = itemTop - (viewportHeight - itemHeight) / 2
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
        return ideal.coerceIn(0, maxScroll)
    }
}
