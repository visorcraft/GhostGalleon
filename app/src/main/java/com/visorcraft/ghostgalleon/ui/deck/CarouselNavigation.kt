package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action

class CarouselNavigation(private val itemCount: Int) {

    fun move(index: Int, action: Action): Int {
        val target = when (action) {
            Action.NAV_LEFT, Action.PAGE_PREV -> index - 1
            Action.NAV_RIGHT, Action.PAGE_NEXT -> index + 1
            else -> index
        }
        return target.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    }

    /** True when [index] is at or before the start of the rail. */
    fun isAtStart(index: Int): Boolean = itemCount <= 0 || index <= 0

    /** True when [index] is at or beyond the end of the rail. */
    fun isAtEnd(index: Int): Boolean = itemCount <= 0 || index >= itemCount - 1

    /**
     * Signed horizontal edge-bump direction (`-1` start wall, `+1` end wall,
     * `0` normal navigation or empty rail) for tactile overscroll feedback.
     */
    fun edgeBumpDirection(index: Int, action: Action): Int {
        if (itemCount <= 0) return 0
        return when (action) {
            Action.NAV_LEFT, Action.PAGE_PREV -> if (isAtStart(index)) -1 else 0
            Action.NAV_RIGHT, Action.PAGE_NEXT -> if (isAtEnd(index)) 1 else 0
            else -> 0
        }
    }
}
