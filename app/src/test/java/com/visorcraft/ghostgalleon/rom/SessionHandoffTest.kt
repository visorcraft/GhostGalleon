package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHandoffTest {

    private fun keepRa() = SessionSurface(
        key = "rom:snes:a.sfc",
        playerId = "ra-snes9x",
        packageName = SessionHandoff.RA_PACKAGE,
        policy = SessionPolicy.KEEP_COMPANION,
        launchDisplayId = 0,
    )

    private fun target(key: String, player: String, pkg: String, policy: SessionPolicy) =
        SessionRingEntry(key, player, pkg, policy, 1L, "t")

    @Test
    fun `isRaPlayer`() {
        assertTrue(SessionHandoff.isRaPlayer("ra-snes9x", "x"))
        assertTrue(SessionHandoff.isRaPlayer(null, SessionHandoff.RA_PACKAGE))
        assertFalse(SessionHandoff.isRaPlayer("drastic", "com.dsemu.drastic"))
    }

    @Test
    fun `same key is no-op without prep`() {
        val t = target(keepRa().key, keepRa().playerId!!, keepRa().packageName, SessionPolicy.KEEP_COMPANION)
        val p = SessionHandoff.plan(keepRa(), t, true, true)
        assertEquals(SwitchToResult.NO_OP, p.result)
        assertEquals(HandoffPrep.NONE, p.prep)
    }

    @Test
    fun `yield current refuses`() {
        val cur = keepRa().copy(policy = SessionPolicy.YIELD_BOTH, playerId = "melondualds")
        val t = target("rom:snes:b.sfc", "ra-snes9x", SessionHandoff.RA_PACKAGE, SessionPolicy.KEEP_COMPANION)
        val p = SessionHandoff.plan(cur, t, true, true)
        assertEquals(SwitchToResult.REFUSE_YIELD, p.result)
        assertEquals(HandoffPrep.NONE, p.prep)
    }

    @Test
    fun `ra keep to other title preps when toggle on`() {
        val t = target("rom:gba:b.gba", "ra-mgba", SessionHandoff.RA_PACKAGE, SessionPolicy.KEEP_COMPANION)
        val on = SessionHandoff.plan(keepRa(), t, true, true)
        assertEquals(SwitchToResult.LAUNCH, on.result)
        assertEquals(HandoffPrep.RA_PAUSE_SAVE, on.prep)
        val off = SessionHandoff.plan(keepRa(), t, true, false)
        assertEquals(HandoffPrep.NONE, off.prep)
        val talkOff = SessionHandoff.plan(keepRa(), t, false, true)
        assertEquals(HandoffPrep.NONE, talkOff.prep)
    }

    @Test
    fun `drastic keep launches without prep`() {
        val cur = keepRa().copy(playerId = "drastic", packageName = "com.dsemu.drastic")
        val t = target("rom:gba:b.gba", "ra-mgba", SessionHandoff.RA_PACKAGE, SessionPolicy.KEEP_COMPANION)
        val p = SessionHandoff.plan(cur, t, true, true)
        assertEquals(SwitchToResult.LAUNCH, p.result)
        assertEquals(HandoffPrep.NONE, p.prep)
    }
}
