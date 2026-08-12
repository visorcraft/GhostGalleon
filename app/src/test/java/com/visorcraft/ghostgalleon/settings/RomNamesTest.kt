package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.rom.RomEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class RomNamesTest {

    private val rom = RomEntry(
        id = "arcade:mslug.zip",
        name = "mslug",
        platformId = "arcade",
        uri = "content://x",
        path = "/x",
    )

    @Test
    fun `display prefers override`() {
        assertEquals("mslug", RomNames.display(rom, emptyMap()))
        assertEquals(
            "Metal Slug",
            RomNames.display(rom, mapOf(rom.id to "Metal Slug")),
        )
        assertEquals("mslug", RomNames.display(rom, mapOf(rom.id to "  ")))
    }

    @Test
    fun `set and clear`() {
        val with = RomNames.set(emptyMap(), rom.id, "Metal Slug")
        assertEquals("Metal Slug", with[rom.id])
        assertEquals(emptyMap<String, String>(), RomNames.set(with, rom.id, "  "))
        assertEquals(emptyMap<String, String>(), RomNames.clear(with, rom.id))
    }
}
