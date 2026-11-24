package com.visorcraft.ghostgalleon.ui.deck

/**
 * Grid glyph size from the Settings icon-size slider, capped so a large
 * request cannot overflow the cell. Pure; host-tested.
 */
object GridIconMetrics {

    const val MIN_DP = 48
    const val MAX_DP = 128

    /**
     * Pixel icon size: [iconSizeDp] scaled by [density], never larger than
     * 92% of [cellWidthPx] so labels and padding still fit.
     */
    fun iconSizePx(cellWidthPx: Int, iconSizeDp: Int, density: Float): Int {
        val cell = cellWidthPx.coerceAtLeast(1)
        val want = (iconSizeDp.coerceIn(MIN_DP, MAX_DP) * density.coerceAtLeast(0.1f))
            .toInt()
            .coerceAtLeast(1)
        val cap = (cell * 0.92f).toInt().coerceAtLeast(1)
        return want.coerceAtMost(cap)
    }

    /**
     * Pixel icon size capped against BOTH [cellWidthPx] and the usable inner
     * height inside [cellHeightPx] (after [cellPaddingPx] on top/bottom and
     * [labelHeightPx]) so icons never clip or squash labels on short panels.
     */
    fun iconSizePx(
        cellWidthPx: Int,
        cellHeightPx: Int,
        cellPaddingPx: Int,
        labelHeightPx: Int,
        iconSizeDp: Int,
        density: Float,
    ): Int {
        val widthCapped = iconSizePx(cellWidthPx, iconSizeDp, density)
        val innerHeight = (cellHeightPx - cellPaddingPx * 2 - labelHeightPx).coerceAtLeast(1)
        val heightCap = (innerHeight * 0.96f).toInt().coerceAtLeast(1)
        return widthCapped.coerceAtMost(heightCap)
    }
}
