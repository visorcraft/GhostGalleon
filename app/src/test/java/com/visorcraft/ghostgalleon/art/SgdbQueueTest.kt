package com.visorcraft.ghostgalleon.art

import com.visorcraft.ghostgalleon.rom.RomEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SgdbQueueTest {

    private fun rom(id: String, name: String = id, artUri: String? = null) = RomEntry(
        id = id,
        name = name,
        platformId = "snes",
        uri = "content://$id",
        path = null,
        artUri = artUri,
    )

    @Test
    fun `prioritize heroes-first then full scrapes`() {
        val entries = listOf(
            rom("a"), // need both
            rom("b"), // hero only
            rom("c", artUri = "file://x"), // has artUri → grid ok; hero missing
            rom("d"), // both cached → drop
        )
        val hasGrid = setOf("b", "d")
        val hasHero = setOf("d")
        val q = SgdbQueue.prioritize(
            entries,
            hasGrid = { it in hasGrid },
            hasHero = { it in hasHero },
        )
        assertEquals(listOf("b", "c", "a"), q.map { it.entry.id })
        assertTrue(q[0].heroOnly)
        assertTrue(q[1].needHero && !q[1].needGrid) // artUri + no hero
        assertTrue(q[2].needGrid && q[2].needHero)
    }

    @Test
    fun `skipMiss drops entries before work`() {
        val q = SgdbQueue.prioritize(
            listOf(rom("a"), rom("b")),
            hasGrid = { false },
            hasHero = { false },
            skipMiss = { it.id == "a" },
        )
        assertEquals(listOf("b"), q.map { it.entry.id })
    }

    @Test
    fun `workerCount bounds by jobs and max`() {
        assertEquals(0, SgdbQueue.workerCount(0))
        assertEquals(1, SgdbQueue.workerCount(1))
        assertEquals(2, SgdbQueue.workerCount(10, maxWorkers = 2))
        assertEquals(1, SgdbQueue.workerCount(10, maxWorkers = 1))
        assertEquals(4, SgdbQueue.workerCount(100, maxWorkers = 9)) // clamp to 4
    }
}
