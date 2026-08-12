package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameMetaTest {

    @Test
    fun `year from parentheses`() {
        assertEquals("1991", FilenameMeta.yearFromLabel("Zelda (1991)"))
        assertEquals("2017", FilenameMeta.yearFromLabel("BotW (USA) (2017)"))
        assertNull(FilenameMeta.yearFromLabel("Zelda (USA)"))
        assertNull(FilenameMeta.yearFromLabel("1899 (1899)"))
    }
}
