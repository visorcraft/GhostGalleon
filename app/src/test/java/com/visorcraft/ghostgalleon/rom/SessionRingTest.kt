package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRingTest {

    private fun e(key: String, player: String? = "ra-snes9x", t: Long = 1L) =
        SessionRingEntry(key, player, "com.retroarch.aarch64", SessionPolicy.KEEP_COMPANION, t, key)

    @Test
    fun `push dedupes by key and caps at 8`() {
        var ring = emptyList<SessionRingEntry>()
        repeat(9) { i -> ring = SessionRing.push(ring, e("k$i", t = i.toLong())) }
        assertEquals(8, ring.size)
        assertEquals("k8", ring.first().key)
        assertTrue(ring.none { it.key == "k0" })
        ring = SessionRing.push(ring, e("k5", t = 99L))
        assertEquals("k5", ring.first().key)
        assertEquals(8, ring.size)
        assertEquals(1, ring.count { it.key == "k5" })
        assertEquals(99L, ring.first().launchedAtMs)
    }

    @Test
    fun `switch decide`() {
        val tgt = e("rom:snes:a.smc", "ra-snes9x")
        assertEquals(
            SwitchToResult.NO_OP,
            SessionSwitch.decide("rom:snes:a.smc", "ra-snes9x", SessionPolicy.KEEP_COMPANION, false, tgt),
        )
        assertEquals(
            SwitchToResult.REFUSE_YIELD,
            SessionSwitch.decide("rom:nds:b.nds", "melondualds", SessionPolicy.YIELD_BOTH, false, tgt),
        )
        assertEquals(
            SwitchToResult.REFUSE_YIELD,
            SessionSwitch.decide("rom:snes:x.smc", "ra-snes9x", SessionPolicy.KEEP_COMPANION, true, tgt),
        )
        assertEquals(
            SwitchToResult.LAUNCH,
            SessionSwitch.decide("rom:gba:c.gba", "ra-mgba", SessionPolicy.KEEP_COMPANION, false, tgt),
        )
    }

    @Test
    fun `titleFor prefers rom name then app label then key`() {
        assertEquals("Chrono Trigger", SessionRing.titleFor("Chrono Trigger", "RA", "rom:snes:ct.smc"))
        assertEquals("Firefox", SessionRing.titleFor(null, "Firefox", "org.mozilla.firefox"))
        assertEquals("org.mozilla.firefox", SessionRing.titleFor(null, null, "org.mozilla.firefox"))
        assertEquals("org.mozilla.firefox", SessionRing.titleFor("", "  ", "org.mozilla.firefox"))
    }
}
