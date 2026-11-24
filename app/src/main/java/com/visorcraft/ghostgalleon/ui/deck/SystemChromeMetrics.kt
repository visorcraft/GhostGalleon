package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import kotlin.math.roundToInt

/**
 * Pure, host-tested placement geometry and visual tokens for the bottom-edge
 * system chrome buttons (Swap at bottom-start, Settings at bottom-end) in
 * [SystemChromeIcons].
 *
 * Aligns both 40dp circular chrome buttons strictly to the live vertical
 * centerline of the floating [DockBar] pill across Sugar (~540dp) and wide
 * 16:9 handhelds (Odin 2 Portal) while preserving >= 8dp horizontal clearance.
 */
object SystemChromeMetrics {

    const val BUTTON_SIZE_DP: Int = 40
    const val DOCK_BAR_PAD_V_DP: Int = 8
    const val MIN_HORIZONTAL_GAP_DP: Int = 8
    const val MIN_BOTTOM_MARGIN_DP: Int = 4
    const val CHROME_ELEVATION_DP: Float = 4f
    const val PRESS_SCALE: Float = 0.92f
    const val FOCUS_SCALE: Float = 1.08f
    const val PRESS_ANIM_MS: Long = 80L

    /** Default circular chrome scrim base RGB (Ghost fallback). */
    const val CHROME_SCRIM_RGB: Int = 0xFF16161C.toInt()
    const val SWAP_ACCENT_BLEND: Float = 0.45f

    /**
     * Unified press + gamepad-focus scale state machine for circular chrome
     * buttons (`IconButton` / `SystemChromeIcons`). Press takes precedence over
     * focus so taps always depress crisply, then settle back to [focusScale]
     * if focus remains.
     */
    fun buttonScale(
        pressed: Boolean,
        focused: Boolean,
        pressScale: Float = PRESS_SCALE,
        focusScale: Float = FOCUS_SCALE,
    ): Float = when {
        pressed -> pressScale
        focused -> focusScale
        else -> 1f
    }

    /** Theme-synced opaque base RGB matching [TileBackgrounds.chromeButton]'s sunken scrim. */
    fun chromeScrimRgb(tokens: com.visorcraft.ghostgalleon.settings.ThemeTokens): Int =
        ThemePack.surfaceSunken(tokens)

    /**
     * Luminous accent-kissed glyph tint for the dual-screen Swap button,
     * keeping high WCAG contrast (> 4.5:1) over the chrome scrim across all themes.
     */
    fun swapIconTint(
        accent: Int,
        scrimRgb: Int = CHROME_SCRIM_RGB,
    ): Int {
        val lifted = ThemePack.accentTextColor(accent, scrimRgb)
        return MoveChoreography.blendRgb(UiDimens.ALPHA_PRIMARY, lifted, SWAP_ACCENT_BLEND)
    }

    /** Crisp white glyph tint for the Settings chrome button. */
    fun settingsIconTint(): Int = UiDimens.ALPHA_PRIMARY

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
        val minBottomPx = (MIN_BOTTOM_MARGIN_DP * d).roundToInt().coerceAtLeast(1)
        val bottomMarginPx = (dockCenterFromBottomPx - buttonSizePx / 2).coerceAtLeast(minBottomPx)

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
