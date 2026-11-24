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
    fun `4-stop scrim protects top and bottom chrome monotonically with denser compact-height profile`() {
        val standard = DeckWallpaper.scrimColors()
        val compact = DeckWallpaper.scrimColorsFor(compactHeight = true)
        assertEquals(4, standard.size)
        assertEquals(4, compact.size)

        for ((name, stops) in listOf("standard" to standard, "compact" to compact)) {
            val topA = (stops[0] ushr 24) and 0xFF
            val upperMidA = (stops[1] ushr 24) and 0xFF
            val lowerMidA = (stops[2] ushr 24) and 0xFF
            val botA = (stops[3] ushr 24) and 0xFF
            assertTrue("$name: top ($topA) > upperCenter ($upperMidA)", topA > upperMidA)
            assertTrue("$name: lowerCenter ($lowerMidA) > upperCenter ($upperMidA)", lowerMidA > upperMidA)
            assertTrue("$name: bottom ($botA) > lowerCenter ($lowerMidA)", botA > lowerMidA)
            assertTrue("$name: bottom ($botA) > top ($topA) to anchor HintBar/dock", botA > topA)
        }
        // Compact panels (e.g. 540dp Sugar) apply stronger or equal protection at every stop.
        for (i in 0 until 4) {
            val stdA = (standard[i] ushr 24) and 0xFF
            val cmpA = (compact[i] ushr 24) and 0xFF
            assertTrue("compact stop[$i] ($cmpA) < standard ($stdA)", cmpA >= stdA)
        }
        assertTrue(DeckWallpaper.isCompactHeightDp(540f))
        assertFalse(DeckWallpaper.isCompactHeightDp(720f))
    }

    @Test
    fun `dual-display cache keys isolate Sugar top 16-9 and bottom 4-3 resolution buckets`() {
        val uri = "content://wall/handheld.jpg"
        val topKey = DeckWallpaper.cacheKeyFor(uri, widthPx = 1920, heightPx = 1080)
        val bottomKey = DeckWallpaper.cacheKeyFor(uri, widthPx = 1080, heightPx = 1080)
        assertEquals(2048, topKey.bucketedMaxDim)
        assertEquals(1280, bottomKey.bucketedMaxDim)
        org.junit.Assert.assertNotEquals(topKey, bottomKey)
        assertTrue(DeckWallpaper.cacheKeySatisfies(topKey, topKey))
        assertFalse(DeckWallpaper.cacheKeySatisfies(topKey, bottomKey))
        assertFalse(DeckWallpaper.cacheKeySatisfies(topKey, DeckWallpaper.cacheKeyFor("content://wall/other.jpg", 1920, 1080)))
        assertFalse(DeckWallpaper.cacheKeySatisfies(DeckWallpaper.CacheKey("", 1280), DeckWallpaper.CacheKey("", 1280)))
        assertEquals(2, DeckWallpaper.MAX_CACHE_ENTRIES)
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

    @Test
    fun `isBitmapUsable rejects recycled or empty dimensions`() {
        assertTrue(DeckWallpaper.isBitmapUsable(recycled = false, width = 1920, height = 1080))
        assertFalse(DeckWallpaper.isBitmapUsable(recycled = true, width = 1920, height = 1080))
        assertFalse(DeckWallpaper.isBitmapUsable(recycled = false, width = 0, height = 1080))
        assertFalse(DeckWallpaper.isBitmapUsable(recycled = false, width = 1920, height = 0))
        assertFalse(DeckWallpaper.isBitmapUsable(recycled = false, width = -4, height = 100))
    }
}
