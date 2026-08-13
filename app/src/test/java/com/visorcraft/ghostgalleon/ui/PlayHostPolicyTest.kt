package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.rom.SessionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHostPolicyTest {

    @Test
    fun `keep on a different display is allowed`() {
        assertTrue(
            PlayHostPolicy.playHostAllowed(
                dualMode = true,
                policy = SessionPolicy.KEEP_COMPANION,
                greedy = false,
                hostDisplayId = 1,
                launchDisplayId = 0,
            ),
        )
    }

    @Test
    fun `denied when same display yield greedy single or missing ids`() {
        val keep = SessionPolicy.KEEP_COMPANION
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, keep, false, 0, 0),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(
                true, SessionPolicy.YIELD_BOTH, false, 1, 0,
            ),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, keep, true, 1, 0),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(false, keep, false, 1, 0),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, keep, false, null, 0),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, keep, false, 1, null),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, null, false, 1, 0),
        )
    }

    @Test
    fun `oracleMaySample table`() {
        assertTrue(
            PlayHostPolicy.oracleMaySample(
                dualMode = true,
                ownsCompanionDisplay = false,
                windowDisplayId = 1,
                launchDisplayId = 0,
                sessionOpen = true,
            ),
        )
        assertFalse(
            PlayHostPolicy.oracleMaySample(true, false, 0, 0, sessionOpen = true),
        )
        assertFalse(
            PlayHostPolicy.oracleMaySample(true, true, 1, 0, sessionOpen = true),
        )
        assertFalse(
            PlayHostPolicy.oracleMaySample(false, false, 1, 0, false),
        )
        assertTrue(
            PlayHostPolicy.oracleMaySample(true, false, 1, 0, sessionOpen = false),
        )
    }
}
