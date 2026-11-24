package com.visorcraft.ghostgalleon.ui.deck

import kotlin.math.roundToInt

/**
 * Pure, host-tested responsive carousel geometry, adaptive card typography,
 * live chrome vertical reserves, and edge-bump timing metrics covering compact
 * dual displays (~540dp Sugar), 16:9 widescreen handhelds (Odin 2 Portal), and
 * narrow portrait panels.
 */
object CarouselLayoutMetrics {

    /** Minimum usable carousel cover art size (dp). */
    const val MIN_CARD_SIZE_DP: Int = 96

    /** Minimum horizontal/vertical RecyclerView edge padding (dp). */
    const val MIN_EDGE_PAD_DP: Int = 16

    /** Base vertical chrome reserve (dp) without HintBar or optional top strips. */
    const val BASE_CHROME_RESERVE_DP: Int = 168

    /** Additional vertical reserve (dp) when the bottom HintBar is visible. */
    const val HINT_BAR_RESERVE_DP: Int = 24

    /** Additional vertical reserve (dp) when the contextual multi-select banner is active. */
    const val MULTI_SELECT_RESERVE_DP: Int = 56

    /** Additional vertical reserve (dp) when the active filter pill strip is visible. */
    const val ACTIVE_FILTER_RESERVE_DP: Int = 40

    /** Additional vertical reserve (dp) when the A–Z letter jump bar is visible. */
    const val LETTER_JUMP_RESERVE_DP: Int = 40

    /** Baseline vertical chrome reserve (dp) for browse bar, dock, hint bar, and card padding. */
    const val CHROME_VERTICAL_RESERVE_DP: Int = BASE_CHROME_RESERVE_DP + HINT_BAR_RESERVE_DP

    /** Focus-scale headroom factor ensuring 1.10x lifted center cards never clip. */
    const val FOCUS_HEADROOM_FACTOR: Float = 1.12f

    /** Horizontal micro-nudge amplitude (dp) when hitting rail start/end. */
    const val EDGE_BUMP_DP: Float = 8f

    /** Full 2-phase (0 → nudge → 0) duration (ms) for edge overscroll micro-nudge. */
    const val EDGE_BUMP_MS: Long = 140L

    /** Minimum card art size (dp) to unlock uniform 2-line carousel card titles. */
    const val TWO_LINE_MIN_CARD_SIZE_DP: Int = 148

    /** Minimum viewport height (dp) to unlock uniform 2-line carousel card titles. */
    const val TWO_LINE_MIN_VIEWPORT_HEIGHT_DP: Int = 560

    /** Supported theme fontScale bounds matching `ThemePack.parseJson`. */
    const val MIN_FONT_SCALE: Float = 0.85f
    const val MAX_FONT_SCALE: Float = 1.30f

    const val MIN_TITLE_SP: Float = 11.5f
    const val MAX_TITLE_SP: Float = 17.0f
    const val MIN_META_SP: Float = 9.5f
    const val MAX_META_SP: Float = 13.5f

    /**
     * Adaptive card title & metadata typography specification. Uses uniform
     * `titleMinLines == titleMaxLines` so 1-line and 2-line titles share an
     * identical vertical baseline across adjacent carousel cards.
     */
    data class CardTypography(
        val titleSp: Float,
        val metaSp: Float,
        val titleMaxLines: Int,
        val titleMinLines: Int,
        val titleTopMarginDp: Int,
        val metaTopMarginDp: Int,
        val estimatedTextHeightDp: Int,
    )

    /**
     * Live vertical chrome reserve (dp) accounting for active optional bars
     * (multi-select banner, active filter pill strip, A–Z letter jump bar) and
     * whether the bottom HintBar is enabled.
     */
    fun verticalChromeReserveDp(
        hasMultiSelect: Boolean,
        hasActiveFilters: Boolean,
        hasLetterJump: Boolean,
        showHints: Boolean,
    ): Int {
        var total = BASE_CHROME_RESERVE_DP
        if (showHints) total += HINT_BAR_RESERVE_DP
        if (hasMultiSelect) total += MULTI_SELECT_RESERVE_DP
        if (hasActiveFilters) total += ACTIVE_FILTER_RESERVE_DP
        if (hasLetterJump) total += LETTER_JUMP_RESERVE_DP
        return total
    }

    /**
     * Compute responsive card typography for [cardSizeDp], [viewportHeightDp],
     * and active theme [fontScale]. Unlocks uniform 2-line titles on roomy
     * cards (`>= 148dp` & `>= 560dp` height, e.g. Odin 2 Portal) while keeping
     * Sugar panels crisp 1-line.
     */
    fun cardTypography(
        cardSizeDp: Int,
        viewportHeightDp: Int,
        fontScale: Float = 1f,
    ): CardTypography {
        val safeCard = cardSizeDp.coerceAtLeast(MIN_CARD_SIZE_DP)
        val safeHeight = viewportHeightDp.coerceAtLeast(MIN_CARD_SIZE_DP)
        val scale = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        val twoLines = safeCard >= TWO_LINE_MIN_CARD_SIZE_DP &&
            safeHeight >= TWO_LINE_MIN_VIEWPORT_HEIGHT_DP

        val baseTitleSp = when {
            safeCard < 124 -> 12.5f
            safeCard < 180 -> 13.5f
            else -> 14.0f
        }
        val baseMetaSp = if (safeCard < 124) 10.0f else 11.0f

        fun roundHalfSp(value: Float): Float = (value * 2f).roundToInt() / 2f
        val titleSp = roundHalfSp(baseTitleSp * scale).coerceIn(MIN_TITLE_SP, MAX_TITLE_SP)
        val metaSp = roundHalfSp(baseMetaSp * scale).coerceIn(MIN_META_SP, MAX_META_SP)
        val lines = if (twoLines) 2 else 1
        val titleTopMarginDp = if (safeCard < 124) 5 else 6
        val metaTopMarginDp = 2
        val estimatedTextHeightDp = (
            titleTopMarginDp +
                titleSp * 1.25f * lines +
                metaTopMarginDp +
                metaSp * 1.25f
            ).roundToInt()

        return CardTypography(
            titleSp = titleSp,
            metaSp = metaSp,
            titleMaxLines = lines,
            titleMinLines = lines,
            titleTopMarginDp = titleTopMarginDp,
            metaTopMarginDp = metaTopMarginDp,
            estimatedTextHeightDp = estimatedTextHeightDp,
        )
    }

    /**
     * Clamp [requestedCardSizeDp] to fit cleanly inside [viewportWidthDp] ×
     * [viewportHeightDp] with room for active browse chrome, dock, card labels,
     * 1.10× focus scale, and peeking ±1 neighbors.
     */
    fun effectiveCardSizeDp(
        requestedCardSizeDp: Int,
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        chromeVerticalReserveDp: Int = CHROME_VERTICAL_RESERVE_DP,
        cardTextHeightDp: Int = 0,
    ): Int {
        val safeW = viewportWidthDp.coerceAtLeast(MIN_CARD_SIZE_DP)
        val safeH = viewportHeightDp.coerceAtLeast(MIN_CARD_SIZE_DP)
        val safeReserve = chromeVerticalReserveDp.coerceAtLeast(0)
        val safeText = cardTextHeightDp.coerceAtLeast(0)
        val availableForArt = safeH - safeReserve - safeText * FOCUS_HEADROOM_FACTOR
        val maxByHeight = (availableForArt / FOCUS_HEADROOM_FACTOR).toInt()
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
