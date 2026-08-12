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
}
