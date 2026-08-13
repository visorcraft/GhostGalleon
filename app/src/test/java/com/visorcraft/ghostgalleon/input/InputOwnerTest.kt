package com.visorcraft.ghostgalleon.input

import com.visorcraft.ghostgalleon.rom.SessionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputOwnerTest {

    @Test
    fun `keep play host defaults to GAME`() {
        assertEquals(
            InputOwner.GAME,
            InputOwnerPolicy.inputOwner(
                dualMode = true,
                policy = SessionPolicy.KEEP_COMPANION,
                greedy = false,
                playHostAllowed = true,
            ),
        )
    }

    @Test
    fun `yield greedy single and no host are NONE`() {
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(true, SessionPolicy.YIELD_BOTH, false, true),
        )
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(true, SessionPolicy.KEEP_COMPANION, true, true),
        )
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(false, SessionPolicy.KEEP_COMPANION, false, true),
        )
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(true, SessionPolicy.KEEP_COMPANION, false, false),
        )
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(true, null, false, false),
        )
    }

    @Test
    fun `hostClaimed only flips GAME`() {
        assertEquals(InputOwner.HOST, InputOwnerPolicy.effectiveOwner(InputOwner.GAME, true))
        assertEquals(InputOwner.GAME, InputOwnerPolicy.effectiveOwner(InputOwner.GAME, false))
        assertEquals(InputOwner.NONE, InputOwnerPolicy.effectiveOwner(InputOwner.NONE, true))
        assertEquals(InputOwner.HOST, InputOwnerPolicy.effectiveOwner(InputOwner.HOST, false))
    }

    @Test
    fun `focus lock only when GAME and play host`() {
        assertTrue(InputOwnerPolicy.focusLockAllowed(InputOwner.GAME, true))
        assertFalse(InputOwnerPolicy.focusLockAllowed(InputOwner.HOST, true))
        assertFalse(InputOwnerPolicy.focusLockAllowed(InputOwner.NONE, true))
        assertFalse(InputOwnerPolicy.focusLockAllowed(InputOwner.GAME, false))
    }

    @Test
    fun `apply is no-op only when lock owner and allowed match prior`() {
        assertTrue(
            InputOwnerPolicy.applyIsNoop(
                true, InputOwner.GAME, true,
                true, InputOwner.GAME, true,
            ),
        )
        assertFalse(
            InputOwnerPolicy.applyIsNoop(
                null, null, null,
                true, InputOwner.GAME, true,
            ),
        )
        assertFalse(
            InputOwnerPolicy.applyIsNoop(
                true, InputOwner.GAME, true,
                false, InputOwner.GAME, true,
            ),
        )
        assertFalse(
            InputOwnerPolicy.applyIsNoop(
                true, InputOwner.GAME, true,
                true, InputOwner.HOST, true,
            ),
        )
        assertFalse(
            InputOwnerPolicy.applyIsNoop(
                true, InputOwner.GAME, true,
                true, InputOwner.GAME, false,
            ),
        )
    }
}
