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
