package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import kotlin.math.abs

/**
 * Pure, host-tested visual depth and optical corner-radius math for
 * [GameDeck]'s horizontal carousel. Separates focused, immediate-neighbor, and
 * distant cards by scale, opacity, subtle vertical lift, and Z-elevation while
 * minimizing `PAYLOAD_SELECTION` invalidations on every D-pad tick.
 */
object CarouselDepthMetrics {

    /** Focused center card scale. */
    const val SCALE_FOCUSED: Float = 1.10f

    /** Immediate ±1 neighbor card scale. */
    const val SCALE_NEAR: Float = 0.98f

    /** Distant (±2 and beyond) card scale. */
    const val SCALE_FAR: Float = 0.93f

    /** Neutral card scale while the dock holds focus. */
    const val SCALE_DOCK_REST: Float = 0.96f

    /** Focused / multi-selected card opacity. */
    const val ALPHA_FOCUSED: Float = 1.00f

    /** Immediate ±1 neighbor card opacity. */
    const val ALPHA_NEAR: Float = 0.86f

    /** Distant (±2 and beyond) card opacity. */
    const val ALPHA_FAR: Float = 0.72f

    /** Unfocused card opacity while the dock holds focus. */
    const val ALPHA_DOCK_REST: Float = 0.78f

    /** Subtle upward Y-lift (dp) on the focused center card. */
    const val LIFT_Y_FOCUSED_DP: Float = -4f

    /** Baseline Y translation (dp) for non-focused cards. */
    const val LIFT_Y_REST_DP: Float = 0f

    /** Z elevation (dp) for focused, near, and far tiers. */
    const val ELEVATION_Z_FOCUSED_DP: Float = 8f
    const val ELEVATION_Z_NEAR_DP: Float = 2f
    const val ELEVATION_Z_FAR_DP: Float = 0f

    /** Maximum distance tier tracked for distinct visual falloff (0, 1, 2+). */
    const val DEPTH_RADIUS: Int = 2

    /** Minimum concentric inner art corner radius (dp) when outer radius allows. */
    const val MIN_INNER_ART_RADIUS_DP: Int = 6

    /** Base alpha for API 28+ tinted spot/ambient spotlight shadows on focused cards. */
    const val FOCUSED_SHADOW_ALPHA: Int = 0xCC

    /** Neutral black shadow used for non-focused carousel cards. */
    const val UNFOCUSED_SHADOW_COLOR: Int = 0xFF000000.toInt()

    data class CardDepthStyle(
        val scale: Float,
        val alpha: Float,
        val translationYDp: Float,
        val translationZDp: Float,
    )

    /** Quantize signed index distance into tier `0` (center), `1` (near), or `2` (far). */
    fun depthTier(distanceFromSelected: Int): Int =
        abs(distanceFromSelected).coerceAtMost(DEPTH_RADIUS)

    /**
     * Compute deterministic visual depth parameters for the card at [position]
     * given the carousel's active [focusedIndex] (null when selection is unset
     * or outside the current rail), whether the dock currently owns focus
     * ([dockFocused]), and whether this card is part of multi-select.
     */
    fun styleFor(
        position: Int,
        focusedIndex: Int?,
        dockFocused: Boolean,
        multiSelected: Boolean,
    ): CardDepthStyle {
        if (dockFocused || focusedIndex == null) {
            return CardDepthStyle(
                scale = if (multiSelected) 1.0f else SCALE_DOCK_REST,
                alpha = if (multiSelected) ALPHA_FOCUSED else ALPHA_DOCK_REST,
                translationYDp = LIFT_Y_REST_DP,
                translationZDp = if (multiSelected) ELEVATION_Z_NEAR_DP else ELEVATION_Z_FAR_DP,
            )
        }
        return when (depthTier(position - focusedIndex)) {
            0 -> CardDepthStyle(
                scale = SCALE_FOCUSED,
                alpha = ALPHA_FOCUSED,
                translationYDp = LIFT_Y_FOCUSED_DP,
                translationZDp = ELEVATION_Z_FOCUSED_DP,
            )
            1 -> CardDepthStyle(
                scale = if (multiSelected) 1.0f else SCALE_NEAR,
                alpha = if (multiSelected) ALPHA_FOCUSED else ALPHA_NEAR,
                translationYDp = LIFT_Y_REST_DP,
                translationZDp = ELEVATION_Z_NEAR_DP,
            )
            else -> CardDepthStyle(
                scale = if (multiSelected) 1.0f else SCALE_FAR,
                alpha = if (multiSelected) ALPHA_FOCUSED else ALPHA_FAR,
                translationYDp = LIFT_Y_REST_DP,
                translationZDp = if (multiSelected) ELEVATION_Z_NEAR_DP else ELEVATION_Z_FAR_DP,
            )
        }
    }

    /**
     * Minimal sorted list of adapter indices whose depth tier actually changes
     * when selection moves from [previousIndex] to [nextIndex]. Used to issue
     * targeted `PAYLOAD_SELECTION` partial binds instead of full invalidations.
     */
    fun affectedSelectionIndices(
        previousIndex: Int?,
        nextIndex: Int?,
        itemCount: Int,
    ): List<Int> {
        if (itemCount <= 0 || previousIndex == nextIndex) return emptyList()
        val last = itemCount - 1
        val candidates = sortedSetOf<Int>()
        fun addWindow(center: Int?) {
            if (center == null) return
            val lo = (center - DEPTH_RADIUS).coerceIn(0, last)
            val hi = (center + DEPTH_RADIUS).coerceIn(0, last)
            for (i in lo..hi) candidates.add(i)
        }
        addWindow(previousIndex)
        addWindow(nextIndex)
        return candidates.filter { idx ->
            val oldTier = previousIndex?.let { depthTier(idx - it) } ?: DEPTH_RADIUS
            val newTier = nextIndex?.let { depthTier(idx - it) } ?: DEPTH_RADIUS
            oldTier != newTier
        }
    }

    /**
     * Optical concentric inner corner radius (dp) for cover art nested inside a
     * card with [outerCardRadiusDp] and [cellPaddingDp] inset. Never exceeds
     * [outerCardRadiusDp] on ultra-sharp custom themes.
     */
    fun opticalInnerArtRadiusDp(outerCardRadiusDp: Int, cellPaddingDp: Int): Int {
        val outer = outerCardRadiusDp.coerceAtLeast(0)
        val pad = cellPaddingDp.coerceAtLeast(0)
        return (outer - pad).coerceAtLeast(minOf(MIN_INNER_ART_RADIUS_DP, outer))
    }

    /**
     * WCAG 2.1 SC 1.4.11 (`>= 3.0:1`) accessible focus-ring stroke color for
     * carousel cards over [surfaceFill]. Preserves accents that already clear
     * the threshold and lifts darker accents (such as Ghost indigo `#3F51B5`
     * or dark custom colors) cleanly.
     */
    fun accessibleCardFocusStroke(accentColor: Int, surfaceFill: Int): Int =
        DeckThemeContrast.accessibleFocusStroke(accentColor, surfaceFill)

    /**
     * API 28+ tinted spot/ambient spotlight shadow color for carousel cards.
     * Focused ROM cards cast a console-tinted halo; focused app cards cast a
     * theme-tinted halo; bright accents (such as OLED `#E0E0E0`) are luminance-
     * damped so dark OLED backdrops never glare; unfocused cards stay neutral black.
     */
    fun cardFocusShadowColor(
        focused: Boolean,
        platformAccent: Int?,
        themeAccent: Int,
    ): Int {
        if (!focused) return UNFOCUSED_SHADOW_COLOR
        val baseTint = platformAccent ?: themeAccent
        return ThemePack.accentGlowColor(baseTint, FOCUSED_SHADOW_ALPHA)
    }
}
