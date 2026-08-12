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
    fun `RA template needs core file only when path is probeable`() {
        val t = Platforms.SNES.player
        val pkg = PlayerResolver.packageName(t)
        // Built-in RA cores sit in RetroArch's private data dir — unprobeable.
        assertTrue(PlayerReadiness.isReady(t, { it == pkg }, { false }))
        assertFalse(PlayerReadiness.isReady(t, { false }, { true }))
        assertFalse(PlayerReadiness.canProbeCorePath(t.extras.getValue("LIBRETRO")))
        assertTrue(PlayerReadiness.canProbeCorePath("/storage/emulated/0/cores/x.so"))
    }

    @Test
    fun `resolveReady skips missing cores on probeable paths`() {
        val publicCore = Platforms.SATURN.player.copy(
            extras = Platforms.SATURN.player.extras +
                ("LIBRETRO" to "/storage/emulated/0/cores/mednafen_saturn_libretro_android.so"),
        )
        val yabause = Platforms.SATURN.players[1].copy(
            extras = Platforms.SATURN.players[1].extras +
                ("LIBRETRO" to "/storage/emulated/0/cores/yabause_libretro_android.so"),
        )
        val platform = Platforms.SATURN.copy(players = listOf(publicCore, yabause))
        val ready = PlayerReadiness.resolveReady(
            platform,
            preferredPlayerId = null,
            installed = { it == "com.retroarch.aarch64" },
            fileExists = { it.contains("yabause") },
        )
        assertEquals("ra-yabause", ready!!.id)
    }

    @Test
    fun `readyPlayers puts preferred first then remaining registry order`() {
        val a = Platforms.WINDOWS.players[0]
        val b = Platforms.WINDOWS.players[1]
        val platform = Platforms.WINDOWS.copy(players = listOf(a, b))
        val installed = { pkg: String -> pkg == "com.winlator" }
        val ordered = PlayerReadiness.readyPlayers(
            platform,
            preferredPlayerId = "winlator-main",
            installed = installed,
            fileExists = { true },
        )
        assertEquals(listOf("winlator-main", "winlator"), ordered.map { it.id })
    }
}
