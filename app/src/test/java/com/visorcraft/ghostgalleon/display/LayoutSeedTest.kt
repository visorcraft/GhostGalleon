package com.visorcraft.ghostgalleon.display

import com.visorcraft.ghostgalleon.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutSeedTest {

    @Test
    fun `single factory layout is seeded`() {
        assertTrue(
            LayoutSeed.shouldApplySuggestions(
                layoutSeeded = false,
                topologyMode = SurfaceMode.SINGLE,
                stillFactory = true,
            ),
        )
        assertFalse(
            LayoutSeed.shouldApplySuggestions(
                layoutSeeded = false,
                topologyMode = SurfaceMode.DUAL,
                stillFactory = true,
            ),
        )
        assertFalse(
            LayoutSeed.shouldApplySuggestions(
                layoutSeeded = true,
                topologyMode = SurfaceMode.SINGLE,
                stillFactory = true,
            ),
        )
    }

    @Test
    fun `apply writes suggestions and marks seeded`() {
        val metrics = LayoutMetricsResolver.fromWindow(
            400, 800, 160, SurfaceMode.SINGLE, false,
        )
        val next = LayoutSeed.apply(Settings.DEFAULT, metrics)
        assertTrue(next.layoutSeeded)
        assertEquals(metrics.suggestedGridColumns, next.gridColumns)
        assertEquals(metrics.suggestedCardSizeDp, next.cardSizeDp)
    }
}
