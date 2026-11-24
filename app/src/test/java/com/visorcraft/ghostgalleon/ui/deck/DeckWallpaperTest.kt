package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckWallpaperTest {

    @Test
    fun `cache hit only when URI matches and is non-blank`() {
        assertTrue(DeckWallpaper.cacheSatisfies("content://wall/1", "content://wall/1"))
        assertFalse(DeckWallpaper.cacheSatisfies("content://wall/1", "content://wall/2"))
        assertFalse(DeckWallpaper.cacheSatisfies(null, "content://wall/1"))
        assertFalse(DeckWallpaper.cacheSatisfies("", ""))
        assertFalse(DeckWallpaper.cacheSatisfies("content://wall/1", ""))
    }

    @Test
    fun `scrim stops darken top and bottom chrome edges while keeping center lighter`() {
        val stops = DeckWallpaper.scrimColors()
        assertEquals(3, stops.size)
        val topA = (stops[0] ushr 24) and 0xFF
        val midA = (stops[1] ushr 24) and 0xFF
        val botA = (stops[2] ushr 24) and 0xFF
        assertTrue("top scrim should darken more than center ($topA vs $midA)", topA > midA)
        assertTrue("bottom scrim should darken more than center ($botA vs $midA)", botA > midA)
    }

    @Test
    fun `computeSampleSize subsamples to display bounds and enforces GPU max texture limit`() {
        // Exact 1080p wallpaper for 1080p screen -> sample 1.
        assertEquals(1, DeckWallpaper.computeSampleSize(1920, 1080, 1920, 1080))
        // 4K wallpaper for 1080p screen -> sample 2 (1920x1080).
        assertEquals(2, DeckWallpaper.computeSampleSize(3840, 2160, 1920, 1080))
        // Extreme 12000x1000 panorama: height alone wouldn't subsample, but maxTextureDim=2048 forces sample=8 -> 1500px!
        val panoSample = DeckWallpaper.computeSampleSize(12000, 1000, 1920, 1080, maxTextureDim = 2048)
        assertEquals(8, panoSample)
        assertTrue(12000 / panoSample <= 2048)
        // Invalid dimensions fall back safely to 1.
        assertEquals(1, DeckWallpaper.computeSampleSize(0, 1080, 1920, 1080))
        assertEquals(1, DeckWallpaper.computeSampleSize(-10, -20, 1920, 1080))
    }

    @Test
    fun `shouldFadeIn snaps on cache hits and fades only on attached async decodes`() {
        assertFalse(DeckWallpaper.shouldFadeIn(wasCacheHit = true, isAttached = true))
        assertFalse(DeckWallpaper.shouldFadeIn(wasCacheHit = true, isAttached = false))
        assertFalse(DeckWallpaper.shouldFadeIn(wasCacheHit = false, isAttached = false))
        assertTrue(DeckWallpaper.shouldFadeIn(wasCacheHit = false, isAttached = true))
        assertEquals(0f, DeckWallpaper.clampAlpha(-0.5f), 0.001f)
        assertEquals(0.35f, DeckWallpaper.clampAlpha(0.35f), 0.001f)
        assertEquals(1f, DeckWallpaper.clampAlpha(2.0f), 0.001f)
    }
}
