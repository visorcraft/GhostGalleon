package com.visorcraft.ghostgalleon.art

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SgdbMissCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `skips same query inside ttl`() {
        val miss = SgdbMissCache.Miss("id", "Zelda", 1_000L)
        assertTrue(SgdbMissCache.shouldSkip(miss, "Zelda", 1_000L + 60_000L))
        assertFalse(SgdbMissCache.shouldSkip(miss, "Zelda", 1_000L + SgdbMissCache.DEFAULT_TTL_MS + 1))
        assertFalse(SgdbMissCache.shouldSkip(miss, "Mario", 1_000L + 60_000L))
        assertFalse(SgdbMissCache.shouldSkip(null, "Zelda", 1_000L))
    }

    @Test
    fun `record and prune`() {
        val now = 10_000L
        val next = SgdbMissCache.record(emptyMap(), "a", "Query", now)
        assertEquals("Query", next["a"]?.query)
        val stale = next + ("b" to SgdbMissCache.Miss("b", "Old", now - SgdbMissCache.DEFAULT_TTL_MS - 5))
        val pruned = SgdbMissCache.prune(stale, now)
        assertTrue("a" in pruned)
        assertFalse("b" in pruned)
    }

    @Test
    fun `Debouncer coalesces 500 rapid misses into 1 atomic write and flushes cleanly on batch finish and exit`() {
        val file = tmp.root.resolve("sgdb/sgdb_miss.json")
        var misses: Map<String, SgdbMissCache.Miss> = emptyMap()
        var pendingTimer: Runnable? = null
        var scheduledDelayMs = -1L
        var scheduleCount = 0
        var cancelCount = 0
        var writeCount = 0
        val ioQueue = ArrayDeque<Runnable>()

        val debouncer = SgdbMissCache.Debouncer(
            debounceMs = SgdbMissCache.PERSIST_DEBOUNCE_MS,
            scheduleDelayed = { delayMs, r ->
                scheduleCount++
                scheduledDelayMs = delayMs
                pendingTimer = r
            },
            cancelDelayed = { r ->
                if (pendingTimer === r) {
                    cancelCount++
                    pendingTimer = null
                }
            },
            dispatchIo = { r -> ioQueue.addLast(r) },
            writeAction = {
                writeCount++
                SgdbMissCache.writeAtomic(file, misses)
            },
        )

        // 1. Burst of 500 misses arms exactly 1 ~2s timer and performs 0 immediate disk writes!
        repeat(500) { i ->
            misses = SgdbMissCache.record(misses, "snes:rom_$i", "Game $i", 10_000L + i)
            debouncer.markDirty()
        }
        assertEquals(SgdbMissCache.PERSIST_DEBOUNCE_MS, scheduledDelayMs)
        assertEquals("500 misses must coalesce into 1 timer", 1, scheduleCount)
        assertEquals("0 writes before debounce fires", 0, writeCount)
        assertTrue(debouncer.isDirty())

        // Fire debounce timer -> enqueues 1 background write
        pendingTimer!!.run()
        pendingTimer = null
        assertEquals(1, ioQueue.size)
        ioQueue.removeFirst().run()
        assertEquals(1, writeCount)
        assertFalse(debouncer.isDirty())
        assertTrue(file.isFile)
        assertFalse("atomic write must leave no .tmp file", File(file.parentFile, file.name + ".tmp").exists())
        assertEquals(500, JSONObject(file.readText()).length())

        // 2. Scrape batch completion flushAsync cancels timer & writes once on background IO
        repeat(25) { i ->
            misses = SgdbMissCache.record(misses, "gba:rom_$i", "Gba $i", 20_000L + i)
            debouncer.markDirty()
        }
        assertEquals(2, scheduleCount)
        debouncer.flushAsync()
        assertEquals(1, cancelCount)
        assertEquals(1, ioQueue.size)
        ioQueue.removeFirst().run()
        assertEquals(2, writeCount)
        assertEquals(525, JSONObject(file.readText()).length())

        // 3. Process exit flushBlocking writes synchronously once and is a no-op when clean
        misses = SgdbMissCache.record(misses, "ps1:exit", "Exit Game", 30_000L)
        debouncer.markDirty()
        debouncer.flushBlocking()
        assertEquals(3, writeCount)
        assertEquals(526, JSONObject(file.readText()).length())
        debouncer.flushBlocking()
        assertEquals("second flush while clean must be a no-op", 3, writeCount)
    }
}
