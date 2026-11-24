package com.visorcraft.ghostgalleon.ui.deck

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
        org.junit.Assert.assertEquals(3, stops.size)
        val topA = (stops[0] ushr 24) and 0xFF
        val midA = (stops[1] ushr 24) and 0xFF
        val botA = (stops[2] ushr 24) and 0xFF
        assertTrue("top scrim should darken more than center ($topA vs $midA)", topA > midA)
        assertTrue("bottom scrim should darken more than center ($botA vs $midA)", botA > midA)
    }
}
