package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.DockSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockBarMetricsTest {

    @Test
    fun `wide 16-9 Odin 2 Portal keeps dock strictly clear of chrome buttons across 4 to 9 slots`() {
        // 1920px @ 320dpi (density 2.0, 960dp wide) and 1920px @ 400dpi (density 2.5, 768dp wide).
        val cases = listOf(
            Triple(1920, 2.0f, 72),
            Triple(1920, 2.5f, 64),
        )
        for ((widthPx, density, suggestedDp) in cases) {
            for (visible in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
                val spec = DockBarMetrics.compute(
                    windowWidthPx = widthPx,
                    density = density,
                    suggestedSlotDp = suggestedDp,
                    visibleSlots = visible,
                    hasChromeIcons = true,
                )
                val totalFootprint = spec.barWidthPx(visible) + spec.sideInsetPx * 2
                assertTrue(
                    "overlap on w=$widthPx d=$density slots=$visible: footprint=$totalFootprint",
                    totalFootprint <= widthPx,
                )
                assertEquals((DockBarMetrics.CHROME_SIDE_INSET_DP * density).toInt(), spec.sideInsetPx)
                assertTrue(
                    "slot too small on wide panel: ${spec.slotSizePx}",
                    spec.slotSizePx >= (48 * density).toInt(),
                )
            }
        }
    }

    @Test
    fun `narrow One X Sugar secondary panel without chrome icons fits all 9 slots comfortably`() {
        // 1080px @ 320dpi (density 2.0 = 540dp wide), no chrome buttons on smaller dual panel.
        val widthPx = 1080
        val density = 2.0f
        for (visible in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
            val spec = DockBarMetrics.compute(
                windowWidthPx = widthPx,
                density = density,
                suggestedSlotDp = 48,
                visibleSlots = visible,
                hasChromeIcons = false,
            )
            val totalFootprint = spec.barWidthPx(visible) + spec.sideInsetPx * 2
            assertTrue("overflow on Sugar bottom: $totalFootprint > $widthPx", totalFootprint <= widthPx)
            assertEquals((DockBarMetrics.EDGE_MARGIN_DP * density).toInt(), spec.sideInsetPx)
            assertTrue(
                "expected >= 40dp slots on Sugar bottom, got ${spec.slotSizePx / density}dp",
                spec.slotSizePx >= (40 * density).toInt(),
            )
        }
    }

    @Test
    fun `narrow 540dp panel WITH chrome icons never collides with Swap or Settings`() {
        // Worst case: 540dp wide with bottom chrome buttons ON, 4..9 visible slots.
        val widthPx = 1080
        val density = 2.0f
        for (visible in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
            val spec = DockBarMetrics.compute(
                windowWidthPx = widthPx,
                density = density,
                suggestedSlotDp = 48,
                visibleSlots = visible,
                hasChromeIcons = true,
            )
            val totalFootprint = spec.barWidthPx(visible) + spec.sideInsetPx * 2
            assertTrue(
                "collision on 540dp+chrome slots=$visible: footprint=$totalFootprint > $widthPx",
                totalFootprint <= widthPx,
            )
            assertTrue("slot collapsed too small: ${spec.slotSizePx}", spec.slotSizePx >= (32 * density).toInt())
        }
    }

    @Test
    fun `floating dock bottom margin is positive and consistent`() {
        val spec = DockBarMetrics.compute(
            windowWidthPx = 1920,
            density = 2.0f,
            suggestedSlotDp = 64,
            visibleSlots = 5,
            hasChromeIcons = true,
        )
        assertEquals(16, spec.bottomMarginPx)
    }
}
