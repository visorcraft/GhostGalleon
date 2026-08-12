package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitaTitlesTest {

    @Test
    fun `title ids are four letters and five digits`() {
        assertTrue(VitaTitles.isTitleId("PCSE00001"))
        assertTrue(VitaTitles.isTitleId("pcsg12345"))
        assertFalse(VitaTitles.isTitleId("vita"))
        assertFalse(VitaTitles.isTitleId("PCSE0000"))
        assertFalse(VitaTitles.isTitleId("eboot.bin"))
    }

    @Test
    fun `titleIdIn finds the first title-id segment`() {
        assertEquals("PCSE00001", VitaTitles.titleIdIn("psvita/PCSE00001/eboot.bin"))
        assertNull(VitaTitles.titleIdIn("psvita/game.vpk"))
    }

    @Test
    fun `eboot name is recognized case-insensitively`() {
        assertTrue(VitaTitles.isEboot("eboot.bin"))
        assertTrue(VitaTitles.isEboot("EBOOT.BIN"))
        assertFalse(VitaTitles.isEboot("eboot.self"))
    }
}
