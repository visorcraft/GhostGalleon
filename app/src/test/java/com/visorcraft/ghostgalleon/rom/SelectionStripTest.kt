package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.literalArgs
import com.visorcraft.ghostgalleon.i18n.resourceIds
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.RaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionStripTest {

    private fun snesRom(name: String = "Chrono Trigger") = RomEntry(
        id = "snes:chrono.sfc",
        name = name,
        platformId = "snes",
        uri = "content://rom",
        path = "/storage/emulated/0/roms/snes/chrono.sfc",
    )

    @Test
    fun `empty brand fallback is selection prompt`() {
        val m = SelectionStrip.empty()
        assertTrue(m.isEmpty)
        assertFalse(m.isRom)
        assertEquals(text(R.string.app_name), m.title)
        assertEquals(text(R.string.deck_select_game_or_app), m.subtitle)
    }

    @Test
    fun `forApp is selection context not hud`() {
        val m = SelectionStrip.forApp("Firefox")
        assertEquals(dynamicText("Firefox"), m.title)
        assertEquals(text(R.string.label_app), m.subtitle)
        assertNull(m.raLine)
        assertFalse(m.isRom)
        assertFalse(m.isEmpty)
    }

    @Test
    fun `forRom shows platform player play and RA`() {
        val rom = snesRom()
        val m = SelectionStrip.forRom(
            rom = rom,
            preferredPlayerId = Platforms.SNES.players.first().id,
            installed = { true },
            playMeta = dynamicText("12m played"),
            raProgress = RaProgress(
                gameId = 1,
                title = "Chrono Trigger",
                numAwarded = 3,
                numPossible = 10,
            ),
            hasRaCredentials = true,
        )
        assertEquals(dynamicText("Chrono Trigger"), m.title)
        assertEquals(dynamicText("Super Nintendo"), m.subtitle) // or SNES displayName
        assertTrue(R.string.format_player in m.detail!!.resourceIds())
        assertTrue("12m played" in m.detail!!.literalArgs())
        assertTrue(R.string.ra_progress in m.raLine!!.resourceIds())
        assertTrue(m.isRom)
        assertEquals("snes", m.platformId)
    }

    @Test
    fun `forRom without RA credentials omits ra line`() {
        val m = SelectionStrip.forRom(
            rom = snesRom(),
            preferredPlayerId = null,
            installed = { true },
            playMeta = null,
            raProgress = RaProgress(numAwarded = 1, numPossible = 5),
            hasRaCredentials = false,
        )
        assertNull(m.raLine)
    }

    @Test
    fun `strip dimensions leave room for text`() {
        assertTrue(SelectionStrip.ART_SIZE_DP < SelectionStrip.STRIP_HEIGHT_DP)
        assertTrue(SelectionStrip.STRIP_HEIGHT_DP <= 160)
        // Art + vertical padding must fit inside strip height.
        assertTrue(SelectionStrip.ART_SIZE_DP + 16 <= SelectionStrip.STRIP_HEIGHT_DP)
    }

    @Test
    fun `forRom player not installed is still shown as play context`() {
        val m = SelectionStrip.forRom(
            rom = snesRom(),
            preferredPlayerId = Platforms.SNES.players.first().id,
            installed = { false },
            playMeta = null,
            raProgress = null,
            hasRaCredentials = false,
        )
        assertTrue(R.string.format_player_not_installed in m.detail!!.resourceIds())
    }
}
