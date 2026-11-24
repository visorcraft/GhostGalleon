package com.visorcraft.ghostgalleon.ui.deck

import kotlin.math.roundToInt

/**
 * Pure, host-tested placement geometry for the bottom-edge system chrome
 * buttons (Swap at bottom-start, Settings at bottom-end) in [SystemChromeIcons].
 *
 * Aligns both 40dp circular chrome buttons strictly to the live vertical
 * centerline of the floating [DockBar] pill across Sugar (~540dp) and wide
 * 16:9 handhelds (Odin 2 Portal) while preserving >= 8dp horizontal clearance.
 */
object SystemChromeMetrics {

    const val BUTTON_SIZE_DP: Int = 40
    const val DOCK_BAR_PAD_V_DP: Int = 8
    const val MIN_HORIZONTAL_GAP_DP: Int = 8
    const val CHROME_ELEVATION_DP: Float = 4f

    data class Spec(
        val buttonSizePx: Int,
        val edgeMarginPx: Int,
        val bottomMarginPx: Int,
        val elevationPx: Float,
    )

    /**
     * Compute pixel size, horizontal edge inset, and dock-centerline bottom
     * margin from the live [dockSpec] and screen [density].
     */
    fun compute(
        dockSpec: DockBarMetrics.Spec,
        density: Float,
    ): Spec {
        val d = density.coerceAtLeast(0.1f)
        val buttonSizePx = (BUTTON_SIZE_DP * d).roundToInt().coerceAtLeast(1)
        val edgeMarginPx = (DockBarMetrics.EDGE_MARGIN_DP * d).roundToInt().coerceAtLeast(1)
        val dockPadVPx = (DOCK_BAR_PAD_V_DP * d).roundToInt()
        val dockPillHeightPx = dockSpec.slotSizePx + dockPadVPx * 2
        val dockCenterFromBottomPx = dockSpec.bottomMarginPx + dockPillHeightPx / 2
        val bottomMarginPx = (dockCenterFromBottomPx - buttonSizePx / 2).coerceAtLeast(edgeMarginPx)

        return Spec(
            buttonSizePx = buttonSizePx,
            edgeMarginPx = edgeMarginPx,
            bottomMarginPx = bottomMarginPx,
            elevationPx = CHROME_ELEVATION_DP * d,
        )
    }

    /** Convenience overload computing [DockBarMetrics.Spec] from window parameters. */
    fun computeForWindow(
        windowWidthPx: Int,
        density: Float,
        suggestedSlotDp: Int,
        visibleSlots: Int,
    ): Spec {
        val dockSpec = DockBarMetrics.compute(
            windowWidthPx = windowWidthPx,
            density = density,
            suggestedSlotDp = suggestedSlotDp,
            visibleSlots = visibleSlots,
            hasChromeIcons = true,
        )
        return compute(dockSpec, density)
    }
}
