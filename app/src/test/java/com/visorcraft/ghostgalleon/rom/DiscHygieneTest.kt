package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscHygieneTest {

    private fun rom(rel: String, platform: String = "ps1") = RomEntry(
        id = "$platform:$rel",
        name = rel.substringAfterLast('/').substringBeforeLast('.'),
        platformId = platform,
        uri = "content://$rel",
        path = null,
    )

    @Test
    fun `skips bios folders and known stems`() {
        assertTrue(DiscHygiene.skipPath("ps1/bios/scph5501.bin"))
        assertTrue(DiscHygiene.skipPath("snes/firmware/kick.rom"))
        assertTrue(DiscHygiene.skipPath("ps1/scph1001.bin"))
        assertFalse(DiscHygiene.skipPath("ps1/Final Fantasy VII.cue"))
        assertFalse(DiscHygiene.skipPath("snes/Chrono Trigger.smc"))
    }

    @Test
    fun `cue wins over sibling bin`() {
        val kept = DiscHygiene.preferDiscMasters(
            listOf(
                rom("ps1/Game.bin"),
                rom("ps1/Game.cue"),
                rom("ps1/Other.iso"),
            ),
        )
        assertEquals(listOf("ps1/Game.cue", "ps1/Other.iso"), kept.map { DiscHygiene.relativePathOf(it) })
    }

    @Test
    fun `m3u wins over cue and bin`() {
        val kept = DiscHygiene.preferDiscMasters(
            listOf(
                rom("ps1/Game.bin"),
                rom("ps1/Game.cue"),
                rom("ps1/Game.m3u"),
            ),
        )
        assertEquals(listOf("ps1/Game.m3u"), kept.map { DiscHygiene.relativePathOf(it) })
    }

    @Test
    fun `different stems are untouched`() {
        val src = listOf(rom("ps1/A.bin"), rom("ps1/B.bin"))
        assertEquals(src, DiscHygiene.preferDiscMasters(src))
    }
}
