package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSkipTest {

    @Test
    fun `skip only when both mtimes are positive and equal`() {
        assertTrue(TreeSkip.skipWalk(10L, 10L, force = false))
        assertFalse(TreeSkip.skipWalk(10L, 11L, force = false))
        assertFalse(TreeSkip.skipWalk(10L, 10L, force = true))
        assertFalse(TreeSkip.skipWalk(0L, 10L, force = false))
        assertFalse(TreeSkip.skipWalk(10L, 0L, force = false))
    }
}
