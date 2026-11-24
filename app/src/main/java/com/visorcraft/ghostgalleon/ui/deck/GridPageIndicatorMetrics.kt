package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import kotlin.math.roundToInt

/**
 * Pure, host-tested paging geometry, tap-to-jump target calculation, and
 * animated pill interpolation for [GridDeck]'s interactive page indicator strip.
 */
object GridPageIndicatorMetrics {

    const val ACTIVE_PILL_WIDTH_DP: Int = 20
    const val INACTIVE_DOT_WIDTH_DP: Int = 6
    const val PILL_HEIGHT_DP: Int = 6
    const val PILL_CORNER_RADIUS_DP: Int = 3
    const val PILL_ANIM_MS: Long = 180L

    /** Semi-translucent neutral inactive dot color on dark/wallpaper backdrops. */
    const val INACTIVE_DOT_COLOR: Int = 0x52FFFFFF

    const val CHEVRON_ENABLED_ALPHA: Float = 0.78f
    const val CHEVRON_DISABLED_ALPHA: Float = 0.22f

    data class StripState(
        val currentPage: Int,
        val totalPages: Int,
        val canPagePrev: Boolean,
        val canPageNext: Boolean,
    )

    /** Compute 0-based current page, page count, and prev/next boundary state. */
    fun stripState(
        selectedSlot: Int,
        slotCount: Int,
        cellsPerPage: Int,
    ): StripState {
        val safeCells = cellsPerPage.coerceAtLeast(1)
        val safeSlots = slotCount.coerceAtLeast(1)
        val totalPages = ((safeSlots + safeCells - 1) / safeCells).coerceAtLeast(1)
        val clampedSlot = selectedSlot.coerceIn(0, safeSlots - 1)
        val currentPage = (clampedSlot / safeCells).coerceIn(0, totalPages - 1)
        return StripState(
            currentPage = currentPage,
            totalPages = totalPages,
            canPagePrev = currentPage > 0,
            canPageNext = currentPage < totalPages - 1,
        )
    }

    /**
     * Slot index when jumping directly to 0-based [targetPage]: preserves the
     * in-page slot offset whenever possible and clamps cleanly on partial tail pages.
     */
    fun targetSlotForPage(
        targetPage: Int,
        currentSlot: Int,
        slotCount: Int,
        cellsPerPage: Int,
    ): Int {
        val safeSlots = slotCount.coerceAtLeast(1)
        val maxSlot = safeSlots - 1
        val safeCells = cellsPerPage.coerceAtLeast(1)
        val state = stripState(currentSlot, safeSlots, safeCells)
        val clampedPage = targetPage.coerceIn(0, state.totalPages - 1)
        val clampedCurrent = currentSlot.coerceIn(0, maxSlot)
        if (clampedPage == state.currentPage) return clampedCurrent
        val inPageOffset = clampedCurrent % safeCells
        return (clampedPage * safeCells + inPageOffset).coerceIn(0, maxSlot)
    }

    /** Slot index when stepping by [deltaPages] (e.g. -1 prev / +1 next chevron). */
    fun stepPageSlot(
        deltaPages: Int,
        currentSlot: Int,
        slotCount: Int,
        cellsPerPage: Int,
    ): Int {
        val state = stripState(currentSlot, slotCount, cellsPerPage)
        return targetSlotForPage(
            targetPage = state.currentPage + deltaPages,
            currentSlot = currentSlot,
            slotCount = slotCount,
            cellsPerPage = cellsPerPage,
        )
    }

    /** Legible active pill color lifted for dark / dimmed-wallpaper backdrops. */
    fun activePillColor(accent: Int): Int =
        ThemePack.accentTextColor(accent, 0xFF000000.toInt())

    /** Linear width interpolation in pixels (`fraction` in `0f..1f`). */
    fun interpolateWidthPx(startPx: Int, endPx: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        return (startPx + (endPx - startPx) * t).roundToInt().coerceAtLeast(1)
    }

    /** Pure linear ARGB interpolation across all 4 channels (host-testable). */
    fun interpolateArgb(startColor: Int, endColor: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        val sa = (startColor ushr 24) and 0xFF
        val sr = (startColor ushr 16) and 0xFF
        val sg = (startColor ushr 8) and 0xFF
        val sb = startColor and 0xFF
        val ea = (endColor ushr 24) and 0xFF
        val er = (endColor ushr 16) and 0xFF
        val eg = (endColor ushr 8) and 0xFF
        val eb = endColor and 0xFF
        val a = (sa + (ea - sa) * t).roundToInt().coerceIn(0, 255)
        val r = (sr + (er - sr) * t).roundToInt().coerceIn(0, 255)
        val g = (sg + (eg - sg) * t).roundToInt().coerceIn(0, 255)
        val b = (sb + (eb - sb) * t).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
