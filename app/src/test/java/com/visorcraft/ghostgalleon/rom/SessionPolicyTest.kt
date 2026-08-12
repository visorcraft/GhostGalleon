package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPolicyTest {

    @Test
    fun `parse blanks and junk keep`() {
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.parse(null))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.parse(""))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.parse("nope"))
        assertEquals(SessionPolicy.YIELD_BOTH, SessionPolicy.parse("YIELD_BOTH"))
        assertEquals(SessionPolicy.YIELD_BOTH, SessionPolicy.parse(" yield_both "))
    }

    @Test
    fun `built-in player ids`() {
        assertEquals(SessionPolicy.YIELD_BOTH, SessionPolicy.forPlayerId("melondualds"))
        assertEquals(SessionPolicy.YIELD_BOTH, SessionPolicy.forPlayerId("azahar"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId("melonds"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId("drastic"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId("ra-melonds"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId("ra-snes9x"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId(null))
    }

    @Test
    fun `resolve prefers rom override then package yield then player id`() {
        assertEquals(
            SessionPolicy.KEEP_COMPANION,
            SessionPolicy.resolve("melondualds", romOverride = SessionPolicy.KEEP_COMPANION),
        )
        assertEquals(
            SessionPolicy.YIELD_BOTH,
            SessionPolicy.resolve("ra-snes9x", packageYield = true),
        )
        assertEquals(
            SessionPolicy.YIELD_BOTH,
            SessionPolicy.resolve("melondualds"),
        )
    }
}
