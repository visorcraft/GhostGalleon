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
}
