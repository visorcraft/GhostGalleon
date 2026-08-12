package com.visorcraft.ghostgalleon.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SgdbMissCacheTest {

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
}
