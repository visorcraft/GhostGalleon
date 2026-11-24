package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens

/**
 * Pure, host-tested touch ripple and surface-feedback color/geometry tokens
 * shared by [GridDeck] tiles and [DockBar] slots.
 *
 * Adapts ripple contrast automatically to dark built-in packs (Ghost, Teal,
 * OLED, Neon — soft accent-tinted white highlight) vs light custom surfaces
 * (subtle dark highlight) while clipping strictly to optical tile radii.
 */
object GridTileFeedbackMetrics {

    /** Luminance threshold separating dark surfaces from custom light surfaces. */
    const val LIGHT_SURFACE_LUMINANCE: Double = 0.35

    /** Alpha for dark-surface touch ripples (~22% opacity: tactile yet calm). */
    const val DARK_SURFACE_RIPPLE_ALPHA: Int = 0x38

    /** Alpha for light-surface touch ripples (~15% dark scrim). */
    const val LIGHT_SURFACE_RIPPLE_ALPHA: Int = 0x26

    /** Fraction of lifted accent blended into white ripples on dark surfaces. */
    const val ACCENT_TINT_FRACTION: Float = 0.28f

    /** Proportional blank-slot `+` font bounds (sp) across dense 7-col Sugar to wide 4-col Odin. */
    const val MIN_BLANK_PLUS_SP: Float = 20f
    const val MAX_BLANK_PLUS_SP: Float = 34f

    /**
     * Proportional `+` font size (sp) for a blank GridDeck tile derived from its
     * shorter edge (`min(cellWidthDp, cellHeightDp)`), clamped to `20..34sp`.
     */
    fun blankPlusTextSp(cellWidthDp: Int, cellHeightDp: Int): Float {
        val minSide = minOf(cellWidthDp, cellHeightDp).coerceAtLeast(1)
        val raw = minSide * 0.28f
        val roundedHalf = kotlin.math.round(raw * 2f) / 2f
        return roundedHalf.coerceIn(MIN_BLANK_PLUS_SP, MAX_BLANK_PLUS_SP)
    }

    /** Crisp WCAG-AA accent-lifted `+` glyph tint when a blank grid tile is focused. */
    fun focusedBlankPlusColor(accent: Int, selectedSurfaceFill: Int): Int =
        ThemePack.accentTextColor(accent, selectedSurfaceFill)

    /** True when measured layout bounds are valid (> 0) and width or height changed. */
    fun layoutBoundsChanged(
        width: Int,
        height: Int,
        lastWidth: Int,
        lastHeight: Int,
    ): Boolean = width > 0 && height > 0 && (width != lastWidth || height != lastHeight)

    /**
     * Surface-adaptive ARGB ripple color for [surfaceFill] and [accent].
     * Returns an accent-kissed light ripple on dark themes, or a soft dark
     * ripple if a custom theme uses a light card surface.
     */
    fun rippleColorForSurface(surfaceFill: Int, accent: Int): Int {
        val luminance = ThemePack.relativeLuminance(surfaceFill)
        if (luminance >= LIGHT_SURFACE_LUMINANCE) {
            return MoveChoreography.withAlpha(0xFF000000.toInt(), LIGHT_SURFACE_RIPPLE_ALPHA)
        }
        val liftedAccent = ThemePack.accentTextColor(accent, surfaceFill)
        val tintedWhite = MoveChoreography.blendRgb(
            base = 0xFFFFFFFF.toInt(),
            overlay = liftedAccent,
            fraction = ACCENT_TINT_FRACTION,
        )
        return MoveChoreography.withAlpha(tintedWhite, DARK_SURFACE_RIPPLE_ALPHA)
    }

    /** Corner radius (dp) for a GridDeck slot's foreground touch ripple mask. */
    fun gridTileRippleRadiusDp(tokens: ThemeTokens): Int =
        tokens.cardRadiusDp.coerceAtLeast(0)
}
