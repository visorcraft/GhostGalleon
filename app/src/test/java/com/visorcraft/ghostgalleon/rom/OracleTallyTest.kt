package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleTallyTest {

    @Test
    fun `three luma misses request heal and a copy failure does not count`() {
        var t = OracleTally()
        var heal = false
        repeat(2) {
            val r = OracleTallyLogic.onSample(t, maxLuma = 0, copyFailed = false, nowMs = 0)
            t = r.first
            heal = r.second
        }
        assertFalse(heal)
        val third = OracleTallyLogic.onSample(t, 0, false, 0)
        assertTrue(third.second)
        val fail = OracleTallyLogic.onSample(OracleTally(), null, true, nowMs = 1000L)
        assertFalse(fail.second)
        assertEquals(0, fail.first.misses)
        assertEquals(11_000L, fail.first.backoffUntilMs) // 1000 + 10_000
        val ignored = OracleTallyLogic.onSample(fail.first, 0, false, nowMs = 5000L)
        assertFalse(ignored.second)
        assertEquals(0, ignored.first.misses)
    }

    @Test
    fun `healthy samples skip PixelCopy until the stretch elapses`() {
        var t = OracleTally()
        assertTrue(OracleTallyLogic.shouldCopy(t, nowMs = 0L))
        repeat(OracleTallyLogic.HITS_TO_STRETCH) {
            t = OracleTallyLogic.onSample(t, maxLuma = 40, copyFailed = false, nowMs = 2_000L).first
        }
        assertEquals(OracleTallyLogic.HITS_TO_STRETCH, t.hits)
        assertFalse(OracleTallyLogic.shouldCopy(t, nowMs = 2_000L))
        assertFalse(
            OracleTallyLogic.shouldCopy(
                t,
                nowMs = 2_000L + OracleTallyLogic.STRETCH_GAP_MS - 1,
            ),
        )
        assertTrue(
            OracleTallyLogic.shouldCopy(
                t,
                nowMs = 2_000L + OracleTallyLogic.STRETCH_GAP_MS,
            ),
        )
        assertTrue(OracleTallyLogic.shouldCopy(t, nowMs = 2_000L, stretch = false))
        val miss = OracleTallyLogic.onSample(t, maxLuma = 0, copyFailed = false, nowMs = 20_000L)
        assertEquals(0, miss.first.hits)
        assertTrue(OracleTallyLogic.shouldCopy(miss.first, nowMs = 20_000L))
        assertFalse(OracleTallyLogic.shouldCopy(failBackoff(1_000L), nowMs = 5_000L))
    }

    private fun failBackoff(nowMs: Long): OracleTally =
        OracleTallyLogic.onSample(OracleTally(), null, true, nowMs).first
}
