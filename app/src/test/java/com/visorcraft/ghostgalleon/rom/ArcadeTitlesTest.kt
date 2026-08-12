package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeTitlesTest {

    @Test
    fun `known stems become titles`() {
        assertEquals("Metal Slug", ArcadeTitles.displayName("mslug"))
        assertEquals("Street Fighter II", ArcadeTitles.displayName("SF2"))
        assertEquals("unknownzip", ArcadeTitles.displayName("unknownzip"))
        assertTrue(ArcadeTitles.knownCount() > 50)
    }
}
