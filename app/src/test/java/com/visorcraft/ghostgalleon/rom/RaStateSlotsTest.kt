package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RaStateSlotsTest {

    @Test
    fun `slots are 1 through 8`() {
        assertEquals((1..8).toList(), RaStateSlots.SLOTS)
    }

    @Test
    fun `slot labels stay 1 through 8 regardless of png names`() {
        assertEquals(RaStateSlots.SLOTS, RaStateSlots.slotLabels(emptyList()))
        assertEquals(
            RaStateSlots.SLOTS,
            RaStateSlots.slotLabels(listOf("mario.state1.png", "mario.state9.png")),
        )
    }

    @Test
    fun `thumbs map stateN png names`() {
        assertEquals(
            mapOf(1 to "mario.state1.png", 8 to "zelda.state8.PNG"),
            RaStateSlots.thumbsBySlot(
                listOf(
                    "readme.txt",
                    "mario.state.png",
                    "mario.state1.png",
                    "zelda.state8.PNG",
                    "auto.state.auto.png",
                ),
            ),
        )
    }

    @Test
    fun `unreadable states dir is numbers only`() {
        val missing = File("/no/such/states/dir")
        assertTrue(RaStateSlots.pngNamesIn(missing).isEmpty())
        assertEquals(RaStateSlots.SLOTS, RaStateSlots.slotLabels(RaStateSlots.pngNamesIn(missing)))
    }

    @Test
    fun `pngNamesIn lists only pngs in that directory`() {
        val dir = File.createTempFile("ra-states", "dir").apply {
            delete()
            mkdir()
        }
        try {
            File(dir, "mario.state1.png").writeText("x")
            File(dir, "note.txt").writeText("y")
            val nested = File(dir, "nested").apply { mkdir() }
            File(nested, "hidden.state2.png").writeText("z")
            assertEquals(setOf("mario.state1.png"), RaStateSlots.pngNamesIn(dir).toSet())
        } finally {
            dir.deleteRecursively()
        }
    }
}
