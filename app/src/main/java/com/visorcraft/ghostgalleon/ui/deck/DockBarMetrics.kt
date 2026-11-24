package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.DockSlots

/**
 * Pure, host-tested width-budget & slot sizing math for [DockBar].
 *
 * Guarantees the centered floating dock bar never overlaps the bottom-start
 * Swap / bottom-end Settings chrome buttons (`40dp` icon + `8dp` edge + `8dp`
 * clearance = `56dp` per side) on single-screen handhelds (Odin 2 Portal) or
 * wide dual panels, while staying generously sized on narrow secondary panels
 * (One X Sugar ~540dp) where system chrome icons are absent.
 */
object DockBarMetrics {

    /** 40dp chrome button + 8dp screen margin + 8dp breathing gap. */
    const val CHROME_SIDE_INSET_DP: Int = 56

    /** Minimum outer margin on displays without bottom chrome buttons. */
    const val EDGE_MARGIN_DP: Int = 8

    /** Horizontal padding inside the dock pill container on each side. */
    const val BAR_PAD_H_DP: Int = 8

    /** Floating bottom bezel margin under the dock pill. */
    const val BOTTOM_MARGIN_DP: Int = 8

    /** Preferred horizontal margin on each side of a slot when space allows. */
    const val SLOT_MARGIN_COMFY_DP: Int = 6

    /** Tight horizontal margin on each side of a slot on narrow panels. */
    const val SLOT_MARGIN_TIGHT_DP: Int = 3

    /** Minimum comfortable slot size before tightening margins / adapting to visible count. */
    const val MIN_COMFY_SLOT_DP: Int = 40

    /** Hard lower bound so touch/focus targets stay usable on extreme widths. */
    const val MIN_SLOT_DP: Int = 28

    /** Minimum "+" placeholder glyph size in sp for compact Sugar slots. */
    const val MIN_BLANK_PLUS_SP: Float = 15f

    /** Maximum "+" placeholder glyph size in sp for wide Odin 2 Portal slots. */
    const val MAX_BLANK_PLUS_SP: Float = 26f

    /**
     * Optical inner icon/art pixel size inside a [slotSizePx] dock slot, keeping
     * balanced clearance around the 3dp focus ring on every density.
     */
    fun iconSizePx(slotSizePx: Int): Int {
        val safe = slotSizePx.coerceAtLeast(1)
        return (safe * 40 / 48).coerceIn(1, safe)
    }

    /**
     * Adaptive sp text size for the blank "+" placeholder so tight ~32dp Sugar
     * slots never clip vertically while wide 64dp+ slots stay optically bold.
     */
    fun blankPlusTextSp(slotSizeDp: Int): Float {
        val safeDp = slotSizeDp.coerceAtLeast(MIN_SLOT_DP)
        return (safeDp * 0.44f).coerceIn(MIN_BLANK_PLUS_SP, MAX_BLANK_PLUS_SP)
    }

    data class Spec(
        val slotSizePx: Int,
        val slotMarginHPx: Int,
        val barPadHPx: Int,
        val sideInsetPx: Int,
        val bottomMarginPx: Int,
    ) {
        /** Total rendered width of the dock bar for [visibleSlots] slots. */
        fun barWidthPx(visibleSlots: Int): Int {
            val n = visibleSlots.coerceAtLeast(0)
            return barPadHPx * 2 + n * (slotSizePx + slotMarginHPx * 2)
        }

        /** Optical inner icon/art pixel size for this dock bar spec. */
        fun iconSizePx(): Int = DockBarMetrics.iconSizePx(slotSizePx)
    }

    /**
     * Compute dock slot & margin metrics from window pixel width, density,
     * [suggestedSlotDp] (from `LayoutMetrics`), [visibleSlots] (`4..9`), and
     * whether bottom-edge system chrome buttons share this panel.
     */
    fun compute(
        windowWidthPx: Int,
        density: Float,
        suggestedSlotDp: Int,
        visibleSlots: Int,
        hasChromeIcons: Boolean,
    ): Spec {
        val d = density.coerceAtLeast(0.1f)
        fun dp(v: Int): Int = (v * d).toInt()

        val safeW = windowWidthPx.coerceAtLeast(1)
        val slots = visibleSlots.coerceIn(DockSlots.MIN_VISIBLE, DockSlots.CAPACITY)
        val sideInsetPx = dp(if (hasChromeIcons) CHROME_SIDE_INSET_DP else EDGE_MARGIN_DP)
        val barPadHPx = dp(BAR_PAD_H_DP)
        val bottomMarginPx = dp(BOTTOM_MARGIN_DP)

        val availablePx = (safeW - sideInsetPx * 2).coerceAtLeast(1)
        val innerPx = (availablePx - barPadHPx * 2).coerceAtLeast(1)

        val suggestedPx = dp(suggestedSlotDp.coerceAtLeast(MIN_COMFY_SLOT_DP))
        val minComfyPx = dp(MIN_COMFY_SLOT_DP)
        val minHardPx = dp(MIN_SLOT_DP)
        val comfyMarginPx = dp(SLOT_MARGIN_COMFY_DP)
        val tightMarginPx = dp(SLOT_MARGIN_TIGHT_DP)

        // Prefer uniform sizing across 4..9 slots (CAPACITY) with comfy margins
        // whenever all 9 fit cleanly; otherwise tighten margins and size to the
        // actually visible slot count so the bar NEVER exceeds availablePx.
        val capComfySlot = (innerPx / DockSlots.CAPACITY) - comfyMarginPx * 2
        val capTightSlot = (innerPx / DockSlots.CAPACITY) - tightMarginPx * 2
        val visComfySlot = (innerPx / slots) - comfyMarginPx * 2
        val visTightSlot = (innerPx / slots) - tightMarginPx * 2

        val (rawSlotPx, slotMarginHPx) = when {
            capComfySlot >= minComfyPx ->
                minOf(suggestedPx, capComfySlot) to comfyMarginPx
            capTightSlot >= minComfyPx ->
                minOf(suggestedPx, capTightSlot) to tightMarginPx
            visComfySlot >= minComfyPx ->
                minOf(suggestedPx, visComfySlot) to comfyMarginPx
            else ->
                minOf(suggestedPx, visTightSlot) to tightMarginPx
        }

        // Never allow slotSizePx to push the bar past availablePx even on tiny screens.
        val maxFitPx = ((innerPx / slots) - slotMarginHPx * 2).coerceAtLeast(1)
        val slotSizePx = rawSlotPx.coerceAtLeast(minOf(minHardPx, maxFitPx)).coerceAtMost(maxFitPx)

        return Spec(
            slotSizePx = slotSizePx,
            slotMarginHPx = slotMarginHPx,
            barPadHPx = barPadHPx,
            sideInsetPx = sideInsetPx,
            bottomMarginPx = bottomMarginPx,
        )
    }
}
