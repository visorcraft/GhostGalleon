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
}
