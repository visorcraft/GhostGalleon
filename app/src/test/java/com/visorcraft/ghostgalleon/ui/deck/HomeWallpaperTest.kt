package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWallpaperTest {

    @Test
    fun `sealFlagLadder returns ordered system+lock fallback ladder on API 24+ and empty below`() {
        assertEquals(emptyList<Int>(), HomeWallpaper.sealFlagLadder(23))
        assertEquals(
            listOf(
                HomeWallpaper.FLAG_SYSTEM_MASK or HomeWallpaper.FLAG_LOCK_MASK,
                HomeWallpaper.FLAG_SYSTEM_MASK,
                HomeWallpaper.FLAG_LOCK_MASK,
            ),
            HomeWallpaper.sealFlagLadder(24),
        )
        assertEquals(HomeWallpaper.sealFlagLadder(24), HomeWallpaper.sealFlagLadder(34))
        assertTrue(HomeWallpaper.SEAL_BITMAP_SIZE_PX in 1..64)
    }
}
