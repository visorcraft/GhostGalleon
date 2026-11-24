package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridIconMetricsTest {

    @Test
    fun `icon size honors slider and caps to cell`() {
        assertEquals(72, GridIconMetrics.iconSizePx(200, 72, density = 1f))
        assertEquals(96, GridIconMetrics.iconSizePx(400, 96, density = 1f))
        val capped = GridIconMetrics.iconSizePx(80, 128, density = 1f)
        assertTrue(capped <= 80)
        assertTrue(capped >= 1)
    }

    @Test
    fun `height-aware icon size caps to available vertical space without squashing label`() {
        // Wide cell (240px) but short height (110px) with 8px padding and 24px label -> 70px inner height.
        val size = GridIconMetrics.iconSizePx(
            cellWidthPx = 240,
            cellHeightPx = 110,
            cellPaddingPx = 8,
            labelHeightPx = 24,
            iconSizeDp = 128,
            density = 1f,
        )
        assertTrue("expected icon <= 70px to leave label room, got $size", size <= 70)
        assertTrue(size >= 1)

        // Plenty of height: matches width/slider cap cleanly.
        val roomy = GridIconMetrics.iconSizePx(
            cellWidthPx = 240,
            cellHeightPx = 240,
            cellPaddingPx = 8,
            labelHeightPx = 24,
            iconSizeDp = 72,
            density = 1f,
        )
        assertEquals(72, roomy)

        // Degenerate tiny height never produces <= 0.
        val tiny = GridIconMetrics.iconSizePx(
            cellWidthPx = 120,
            cellHeightPx = 20,
            cellPaddingPx = 8,
            labelHeightPx = 24,
            iconSizeDp = 72,
            density = 1f,
        )
        assertEquals(1, tiny)
    }
}
