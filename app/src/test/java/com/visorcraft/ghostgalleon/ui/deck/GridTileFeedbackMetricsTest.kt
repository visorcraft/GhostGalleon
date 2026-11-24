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

    @Test
    fun `blankPlusTextSp scales proportionally from 7-col Sugar to 4-col Odin and stays clamped`() {
        val sugar7Col = GridTileFeedbackMetrics.blankPlusTextSp(cellWidthDp = 64, cellHeightDp = 76)
        val sugar5Col = GridTileFeedbackMetrics.blankPlusTextSp(cellWidthDp = 92, cellHeightDp = 100)
        val odin4Col = GridTileFeedbackMetrics.blankPlusTextSp(cellWidthDp = 200, cellHeightDp = 180)
        assertEquals(GridTileFeedbackMetrics.MIN_BLANK_PLUS_SP, sugar7Col, 0.001f)
        assertTrue("5-col ($sugar5Col) > 7-col ($sugar7Col)", sugar5Col > sugar7Col)
        assertTrue("Odin 4-col ($odin4Col) > 5-col ($sugar5Col)", odin4Col > sugar5Col)
        assertEquals(GridTileFeedbackMetrics.MAX_BLANK_PLUS_SP, odin4Col, 0.001f)
        assertTrue(sugar5Col in GridTileFeedbackMetrics.MIN_BLANK_PLUS_SP..GridTileFeedbackMetrics.MAX_BLANK_PLUS_SP)

        // Degenerate/extreme inputs still stay safely clamped.
        assertEquals(GridTileFeedbackMetrics.MIN_BLANK_PLUS_SP, GridTileFeedbackMetrics.blankPlusTextSp(0, 0), 0.001f)
        assertEquals(GridTileFeedbackMetrics.MAX_BLANK_PLUS_SP, GridTileFeedbackMetrics.blankPlusTextSp(500, 500), 0.001f)
    }

    @Test
    fun `layoutBoundsChanged detects both width and height transitions while ignoring zero bounds`() {
        org.junit.Assert.assertFalse(GridTileFeedbackMetrics.layoutBoundsChanged(0, 800, -1, -1))
        org.junit.Assert.assertFalse(GridTileFeedbackMetrics.layoutBoundsChanged(1080, 0, -1, -1))
        assertTrue(GridTileFeedbackMetrics.layoutBoundsChanged(1080, 800, -1, -1))
        org.junit.Assert.assertFalse(GridTileFeedbackMetrics.layoutBoundsChanged(1080, 800, 1080, 800))
        // Width-only posture/DPI transition must trigger recompute!
        assertTrue(GridTileFeedbackMetrics.layoutBoundsChanged(1280, 800, 1080, 800))
        // Height-only IME close transition must trigger recompute!
        assertTrue(GridTileFeedbackMetrics.layoutBoundsChanged(1080, 1080, 1080, 800))
    }

    @Test
    fun `focusedBlankPlusColor clears WCAG AA contrast over elevated selected fill on all built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val selectedFill = TileBackgrounds.selectedFillColor(tokens)
            val color = GridTileFeedbackMetrics.focusedBlankPlusColor(tokens.accentColor, selectedFill)
            val ratio = ThemePack.contrastRatio(color, selectedFill)
            assertTrue(
                "${tokens.id} focused blank '+' contrast $ratio < 4.5",
                ratio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }
}
