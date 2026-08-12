package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSurfaceTest {

    @Test
    fun `forLaunch uses player id`() {
        val y = SessionSurface.forLaunch("rom:nds:a.nds", "melondualds", "me.magnum.melondualds", 0)
        assertEquals(SessionPolicy.YIELD_BOTH, y.policy)
        val k = SessionSurface.forLaunch("rom:snes:x.smc", "ra-snes9x", "com.retroarch.aarch64", 0)
        assertEquals(SessionPolicy.KEEP_COMPANION, k.policy)
        assertFalse(k.greedy)
    }

    @Test
    fun `forLaunch packageYield yields`() {
        val y = SessionSurface.forLaunch(
            "rom:snes:x.smc",
            "ra-snes9x",
            "com.retroarch.aarch64",
            0,
            packageYield = true,
        )
        assertEquals(SessionPolicy.YIELD_BOTH, y.policy)
        assertEquals("rom:snes:x.smc", y.key)
        assertEquals("ra-snes9x", y.playerId)
        assertEquals("com.retroarch.aarch64", y.packageName)
        assertEquals(0, y.launchDisplayId)
        assertFalse(y.greedy)
    }

    @Test
    fun `forLaunch greedy copy`() {
        val k = SessionSurface.forLaunch("rom:snes:x.smc", "ra-snes9x", "com.retroarch.aarch64", 0)
        assertFalse(k.greedy)
        val g = k.copy(greedy = true)
        assertTrue(g.greedy)
        assertEquals(k.key, g.key)
        assertEquals(k.policy, g.policy)
        assertEquals(k.playerId, g.playerId)
    }
}
