package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.SessionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Smoke: companion Resume launches the continue/session key via launchSlotKey.
 * KEEP prefers sessionSurface.key so a navigated hero does not steal Resume.
 */
class CompanionResumeKeyTest {

    @Test
    fun `no session uses continue key not selected`() {
        assertEquals(
            "rom:snes:last.sfc",
            CompanionPanel.resumeLaunchKey(
                continueKey = "rom:snes:last.sfc",
                sessionSurfaceKey = null,
                selectedKey = "rom:gba:other.gba",
                sessionPolicy = null,
            ),
        )
    }

    @Test
    fun `KEEP prefers session surface key over selected and continue`() {
        assertEquals(
            "rom:snes:open.sfc",
            CompanionPanel.resumeLaunchKey(
                continueKey = "rom:gba:other.gba",
                sessionSurfaceKey = "rom:snes:open.sfc",
                selectedKey = "rom:gba:other.gba",
                sessionPolicy = SessionPolicy.KEEP_COMPANION,
            ),
        )
    }

    @Test
    fun `KEEP falls back to selected when session key is missing`() {
        assertEquals(
            "rom:snes:hero.sfc",
            CompanionPanel.resumeLaunchKey(
                continueKey = "rom:gba:other.gba",
                sessionSurfaceKey = null,
                selectedKey = "rom:snes:hero.sfc",
                sessionPolicy = SessionPolicy.KEEP_COMPANION,
            ),
        )
    }

    @Test
    fun `KEEP with no keys is null`() {
        assertNull(
            CompanionPanel.resumeLaunchKey(
                continueKey = "rom:gba:other.gba",
                sessionSurfaceKey = null,
                selectedKey = null,
                sessionPolicy = SessionPolicy.KEEP_COMPANION,
            ),
        )
    }

    @Test
    fun `YIELD stays on continue key`() {
        assertEquals(
            "rom:nds:last.nds",
            CompanionPanel.resumeLaunchKey(
                continueKey = "rom:nds:last.nds",
                sessionSurfaceKey = "rom:nds:open.nds",
                selectedKey = "rom:gba:other.gba",
                sessionPolicy = SessionPolicy.YIELD_BOTH,
            ),
        )
    }
}
