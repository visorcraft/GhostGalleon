package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTileFeedbackMetricsTest {

    @Test
    fun `all built-in dark themes produce soft luminous accent-tinted ripples`() {
        for (tokens in ThemePack.BUILTINS) {
            val fill = ThemePack.cardFill(tokens)
            val ripple = GridTileFeedbackMetrics.rippleColorForSurface(fill, tokens.accentColor)
            val alpha = (ripple ushr 24) and 0xFF
            val opaque = (0xFF shl 24) or (ripple and 0x00FFFFFF)
            assertEquals(GridTileFeedbackMetrics.DARK_SURFACE_RIPPLE_ALPHA, alpha)
            assertTrue(
                "theme=${tokens.id} ripple base luminance should be bright (>0.5)",
                ThemePack.relativeLuminance(opaque) > 0.5,
            )
        }
    }

    @Test
    fun `light custom card surface flips cleanly to dark touch ripple`() {
        val lightSurface = 0xFFF2F2F7.toInt()
        val ripple = GridTileFeedbackMetrics.rippleColorForSurface(lightSurface, 0xFF3F51B5.toInt())
        val alpha = (ripple ushr 24) and 0xFF
        val rgb = ripple and 0x00FFFFFF
        assertEquals(GridTileFeedbackMetrics.LIGHT_SURFACE_RIPPLE_ALPHA, alpha)
        assertEquals(0x000000, rgb)
    }

    @Test
    fun `grid tile ripple radius matches theme card radius safely`() {
        assertEquals(24, GridTileFeedbackMetrics.gridTileRippleRadiusDp(ThemePack.GHOST))
        assertEquals(12, GridTileFeedbackMetrics.gridTileRippleRadiusDp(ThemePack.OLED))
        val zero = ThemeTokens(id = "flat", displayName = "Flat", accentColor = 0xFF00AAFF.toInt(), cardRadiusDp = 0)
        assertEquals(0, GridTileFeedbackMetrics.gridTileRippleRadiusDp(zero))
    }
}
