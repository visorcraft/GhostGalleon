package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconDensityMetricsTest {

    @Test
    fun `targetDensityDpi steps cleanly across mosaic, dock, and large grid sizes`() {
        assertEquals(IconDensityMetrics.DENSITY_DEFAULT, IconDensityMetrics.targetDensityDpi(0))
        assertEquals(IconDensityMetrics.DENSITY_DEFAULT, IconDensityMetrics.targetDensityDpi(-16))
        // Folder mosaic mini-tile (~32px–48px) -> MDPI (160)
        assertEquals(IconDensityMetrics.DENSITY_MEDIUM, IconDensityMetrics.targetDensityDpi(32))
        assertEquals(IconDensityMetrics.DENSITY_MEDIUM, IconDensityMetrics.targetDensityDpi(48))
        // Compact Sugar dock icon (60px) -> HDPI (240)
        assertEquals(IconDensityMetrics.DENSITY_HIGH, IconDensityMetrics.targetDensityDpi(60))
        // Standard 48dp dock icon @ 2x (80px–96px) -> XHDPI (320)
        assertEquals(IconDensityMetrics.DENSITY_XHIGH, IconDensityMetrics.targetDensityDpi(80))
        assertEquals(IconDensityMetrics.DENSITY_XHIGH, IconDensityMetrics.targetDensityDpi(96))
        // Standard 72dp grid tile @ 2x (144px) -> XXHDPI (480)
        assertEquals(IconDensityMetrics.DENSITY_XXHIGH, IconDensityMetrics.targetDensityDpi(144))
        // Large Odin 2 Portal tile (192px–320px) -> XXXHDPI (640)
        assertEquals(IconDensityMetrics.DENSITY_XXXHIGH, IconDensityMetrics.targetDensityDpi(192))
        assertEquals(IconDensityMetrics.DENSITY_XXXHIGH, IconDensityMetrics.targetDensityDpi(320))
    }

    @Test
    fun `cacheKey buckets identically within each density tier and matches package cleanly`() {
        assertEquals("com.example.game", IconDensityMetrics.cacheKey("com.example.game", 0))
        assertEquals(
            IconDensityMetrics.cacheKey("com.example.game", 100),
            IconDensityMetrics.cacheKey("com.example.game", 144),
        )
        assertEquals("com.example.game@640", IconDensityMetrics.cacheKey("com.example.game", 240))

        assertTrue(IconDensityMetrics.matchesPackage("com.example.game", "com.example.game"))
        assertTrue(IconDensityMetrics.matchesPackage("com.example.game@480", "com.example.game"))
        assertFalse(IconDensityMetrics.matchesPackage("com.example.game2@480", "com.example.game"))
    }
}
