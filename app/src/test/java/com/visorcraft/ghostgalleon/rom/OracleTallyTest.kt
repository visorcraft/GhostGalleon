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
}
