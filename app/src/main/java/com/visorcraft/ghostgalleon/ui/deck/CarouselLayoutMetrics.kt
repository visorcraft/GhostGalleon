package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure, host-tested responsive carousel geometry and edge-bump timing metrics
 * covering compact dual displays (~540dp Sugar), 16:9 widescreen handhelds
 * (Odin 2 Portal), and narrow portrait panels.
 */
object CarouselLayoutMetrics {

    /** Minimum usable carousel cover art size (dp). */
    const val MIN_CARD_SIZE_DP: Int = 96

    /** Minimum horizontal/vertical RecyclerView edge padding (dp). */
    const val MIN_EDGE_PAD_DP: Int = 16

    /** Vertical chrome reserve (dp) for browse bar, dock, hint bar, and card text. */
    const val CHROME_VERTICAL_RESERVE_DP: Int = 192

    /** Focus-scale headroom factor ensuring 1.10x lifted center cards never clip. */
    const val FOCUS_HEADROOM_FACTOR: Float = 1.12f

    /** Horizontal micro-nudge amplitude (dp) when hitting rail start/end. */
    const val EDGE_BUMP_DP: Float = 8f

    /** Return-to-center duration (ms) for edge overscroll micro-nudge. */
    const val EDGE_BUMP_MS: Long = 130L

    /**
     * Clamp [requestedCardSizeDp] to fit cleanly inside [viewportWidthDp] ×
     * [viewportHeightDp] with room for browse chrome, dock, card labels,
     * 1.10× focus scale, and peeking ±1 neighbors.
     */
    fun effectiveCardSizeDp(
        requestedCardSizeDp: Int,
        viewportWidthDp: Int,
        viewportHeightDp: Int,
    ): Int {
        val safeW = viewportWidthDp.coerceAtLeast(MIN_CARD_SIZE_DP)
        val safeH = viewportHeightDp.coerceAtLeast(MIN_CARD_SIZE_DP)
        val maxByHeight = ((safeH - CHROME_VERTICAL_RESERVE_DP) / FOCUS_HEADROOM_FACTOR).toInt()
        val maxByWidth = (safeW * 0.60f).toInt()
        val upperBound = minOf(maxByHeight, maxByWidth).coerceAtLeast(MIN_CARD_SIZE_DP)
        return requestedCardSizeDp.coerceIn(MIN_CARD_SIZE_DP, upperBound)
    }

    /** Responsive inter-card horizontal gap (dp). */
    fun cardSpacingDp(viewportWidthDp: Int): Int = when {
        viewportWidthDp <= 540 -> 10
        viewportWidthDp <= 780 -> 14
        else -> 18
    }

    /**
     * Symmetric horizontal RecyclerView padding (dp) so index `0` and index
     * `lastIndex` can center gracefully under the focus spotlight while keeping
     * neighboring cards visible on screen.
     */
    fun horizontalEdgePaddingDp(
        viewportWidthDp: Int,
        cardWidthDp: Int,
    ): Int {
        val safeW = viewportWidthDp.coerceAtLeast(MIN_EDGE_PAD_DP * 2)
        val centered = (safeW - cardWidthDp.coerceAtLeast(1)) / 2
        val maxPad = (safeW / 2).coerceAtLeast(MIN_EDGE_PAD_DP)
        return centered.coerceIn(MIN_EDGE_PAD_DP, maxPad)
    }

    /**
     * Exact pixel offset passed to `LinearLayoutManager.scrollToPositionWithOffset`
     * (measured relative to `paddingStart`) to center a card of [cardWidthPx]
     * inside [viewportWidthPx] with [paddingStartPx]/[paddingEndPx].
     */
    fun centerScrollOffsetPx(
        viewportWidthPx: Int,
        paddingStartPx: Int,
        paddingEndPx: Int,
        cardWidthPx: Int,
    ): Int {
        val innerWidth = (viewportWidthPx - paddingStartPx - paddingEndPx).coerceAtLeast(0)
        return ((innerWidth - cardWidthPx) / 2).coerceAtLeast(0)
    }
}
