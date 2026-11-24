package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.rom.SessionRingEntry
import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSwitcherNavTest {

    private fun entry(key: String) = SessionRingEntry(
        key = key,
        playerId = "retroarch_snes",
        packageName = "com.retroarch",
        policy = SessionPolicy.KEEP_COMPANION,
        launchedAtMs = 1_000L,
        title = key,
    )

    @Test
    fun `vertical navigation wraps and page jumps clamp cleanly`() {
        assertEquals(0, SessionSwitcherNav.move(0, count = 0, action = Action.NAV_DOWN))
        assertEquals(2, SessionSwitcherNav.move(0, count = 3, action = Action.NAV_UP))
        assertEquals(0, SessionSwitcherNav.move(2, count = 3, action = Action.NAV_DOWN))
        assertEquals(1, SessionSwitcherNav.move(0, count = 3, action = Action.NAV_DOWN))
        assertEquals(4, SessionSwitcherNav.move(0, count = 6, action = Action.PAGE_NEXT))
        assertEquals(5, SessionSwitcherNav.move(4, count = 6, action = Action.PAGE_NEXT))
        assertEquals(0, SessionSwitcherNav.move(2, count = 6, action = Action.PAGE_PREV))
    }

    @Test
    fun `active entry detection matches exact non-blank key only`() {
        assertTrue(SessionSwitcherNav.isActiveEntry("rom:snes/zelda", "rom:snes/zelda"))
        assertFalse(SessionSwitcherNav.isActiveEntry("rom:snes/zelda", "rom:snes/mario"))
        assertFalse(SessionSwitcherNav.isActiveEntry("rom:snes/zelda", null))
        assertFalse(SessionSwitcherNav.isActiveEntry("rom:snes/zelda", "   "))
    }

    @Test
    fun `initialSelection preserves clamped previous index or selects active entry`() {
        val list = listOf(entry("a"), entry("b"), entry("c"))
        // Fresh open highlights active entry when present.
        assertEquals(1, SessionSwitcherNav.initialSelection(list, previousSelection = null, activeKey = "b"))
        assertEquals(0, SessionSwitcherNav.initialSelection(list, previousSelection = null, activeKey = "missing"))
        // Re-attach after Y removal keeps cursor at clamped index.
        assertEquals(2, SessionSwitcherNav.initialSelection(list, previousSelection = 3, activeKey = "a"))
        assertEquals(1, SessionSwitcherNav.initialSelection(list, previousSelection = 1, activeKey = "a"))
        assertEquals(0, SessionSwitcherNav.initialSelection(emptyList(), previousSelection = 2, activeKey = "a"))
    }
}
