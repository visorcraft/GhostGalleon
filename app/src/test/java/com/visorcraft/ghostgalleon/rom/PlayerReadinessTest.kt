package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerReadinessTest {

    @Test
    fun `libretro path only from RA extras`() {
        val core = PlayerReadiness.libretroCorePath(Platforms.SNES.player)
        assertTrue(core!!.contains("snes9x_libretro"))
        assertNull(PlayerReadiness.libretroCorePath(Platforms.SWITCH.player))
    }

    @Test
    fun `RA template needs core file`() {
        val t = Platforms.SNES.player
        val pkg = PlayerResolver.packageName(t)
        assertFalse(PlayerReadiness.isReady(t, { it == pkg }, { false }))
        assertTrue(PlayerReadiness.isReady(t, { it == pkg }, { true }))
        assertFalse(PlayerReadiness.isReady(t, { false }, { true }))
    }

    @Test
    fun `resolveReady skips missing cores`() {
        val ready = PlayerReadiness.resolveReady(
            Platforms.SATURN,
            preferredPlayerId = null,
            installed = { it == "com.retroarch.aarch64" },
            fileExists = { it.contains("yabause") },
        )
        assertEquals("ra-yabause", ready!!.id)
    }
}
