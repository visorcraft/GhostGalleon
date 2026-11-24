package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack

/**
 * Pure cross-theme contrast, adaptive touch-ripple, and focus-ring resolver
 * ensuring Ghost, Teal, OLED Black, and Neon themes maintain crisp visible
 * focus, press feedback, and WCAG AA foreground contrast. Host-tested.
 */
object CompanionThemeAudit {

    /** Light translucent ripple for dark/saturated pill fills. */
    const val RIPPLE_ON_DARK: Int = 0x38FFFFFF

    /** Dark translucent ripple for bright pill fills (e.g. OLED `#E0E0E0`). */
    const val RIPPLE_ON_LIGHT: Int = 0x2E000000

    /** Adaptive press ripple color guaranteeing clear tap feedback on both dark and bright fills. */
    fun rippleColorForFill(fillColor: Int): Int {
        val opaque = (0xFF shl 24) or (fillColor and 0x00FFFFFF)
        val whiteContrast = ThemePack.contrastRatio(0xFFFFFFFF.toInt(), opaque)
        val blackContrast = ThemePack.contrastRatio(0xFF000000.toInt(), opaque)
        return if (blackContrast > whiteContrast) RIPPLE_ON_LIGHT else RIPPLE_ON_DARK
    }

    /**
     * Contrasting 2dp gamepad focus stroke for interactive pills:
     * - Filled accent pills never draw accent-on-accent (invisible focus); instead
     *   they pick the mathematically higher WCAG 2.1 contrast of pure white vs pure
     *   black against [accentColor] (guaranteed `>= sqrt(21) ≈ 4.58:1` for every RGB color).
     * - Idle dark pills use WCAG-AA lifted [ThemePack.accentTextColor] over [surfaceColor].
     */
    fun focusRingColorForFill(
        filled: Boolean,
        accentColor: Int,
        surfaceColor: Int = ThemePack.DEFAULT_DARK_SURFACE,
    ): Int = if (filled) {
        val opaque = (0xFF shl 24) or (accentColor and 0x00FFFFFF)
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        if (ThemePack.contrastRatio(white, opaque) >= ThemePack.contrastRatio(black, opaque)) {
            white
        } else {
            black
        }
    } else {
        ThemePack.accentTextColor(accentColor, surfaceColor)
    }

    data class KeycapTheme(
        val fillColor: Int,
        val strokeColor: Int,
        val textColor: Int,
        val separatorColor: Int,
    )

    /** Harmonized inline keycap pill colors tuned for OLED pure black vs lifted themes. */
    fun hintKeycapTheme(themeId: String?): KeycapTheme =
        if (themeId.equals(ThemePack.OLED.id, ignoreCase = true)) {
            KeycapTheme(
                fillColor = 0x24FFFFFF,
                strokeColor = 0x58FFFFFF,
                textColor = 0xFFFFFFFF.toInt(),
                separatorColor = 0x60FFFFFF,
            )
        } else {
            KeycapTheme(
                fillColor = 0x26FFFFFF,
                strokeColor = 0x48FFFFFF,
                textColor = 0xFFFFFFFF.toInt(),
                separatorColor = 0x55FFFFFF,
            )
        }

    /** Exact sRGB alpha composite of translucent [foregroundArgb] over opaque [backgroundRgb]. */
    fun compositeAlphaOver(foregroundArgb: Int, backgroundRgb: Int): Int {
        val a = ((foregroundArgb ushr 24) and 0xFF) / 255.0
        val fr = (foregroundArgb shr 16) and 0xFF
        val fg = (foregroundArgb shr 8) and 0xFF
        val fb = foregroundArgb and 0xFF
        val br = (backgroundRgb shr 16) and 0xFF
        val bg = (backgroundRgb shr 8) and 0xFF
        val bb = backgroundRgb and 0xFF
        val r = (fr * a + br * (1.0 - a)).toInt().coerceIn(0, 255)
        val g = (fg * a + bg * (1.0 - a)).toInt().coerceIn(0, 255)
        val b = (fb * a + bb * (1.0 - a)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
