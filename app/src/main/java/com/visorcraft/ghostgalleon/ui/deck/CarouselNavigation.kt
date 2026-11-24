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

    companion object {
        /** Matches `RecyclerView.SCROLL_STATE_IDLE` for pure-JVM testing. */
        const val SCROLL_STATE_IDLE: Int = 0

        /** Matches `RecyclerView.SCROLL_STATE_DRAGGING` for pure-JVM testing. */
        const val SCROLL_STATE_DRAGGING: Int = 1

        /** Matches `RecyclerView.SCROLL_STATE_SETTLING` for pure-JVM testing. */
        const val SCROLL_STATE_SETTLING: Int = 2

        /**
         * True only when a genuine user touch drag/fling has settled to
         * [SCROLL_STATE_IDLE] on a valid snapped card whose key differs from
         * [currentKey]. Programmatic D-pad / browse scrolls never set
         * [wasUserDrag], avoiding selection echo loops.
         */
        fun shouldCommitSnapSelection(
            wasUserDrag: Boolean,
            scrollState: Int,
            snappedKey: String?,
            currentKey: String?,
        ): Boolean = wasUserDrag &&
            scrollState == SCROLL_STATE_IDLE &&
            !snappedKey.isNullOrEmpty() &&
            snappedKey != currentKey
    }
}
