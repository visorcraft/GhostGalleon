package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.input.InputOwner
import com.visorcraft.ghostgalleon.input.InputOwnerPolicy
import com.visorcraft.ghostgalleon.ui.deck.CompanionPanel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostSurfaceTest {

    @Test
    fun `seat helper and cockpit are exclusive`() {
        assertTrue(HostSurfacePolicy.exclusive(HostSurface.SEAT))
        assertTrue(HostSurfacePolicy.exclusive(HostSurface.HELPER))
        assertTrue(HostSurfacePolicy.exclusive(HostSurface.COCKPIT))
        assertFalse(HostSurfacePolicy.exclusive(HostSurface.HUD))
        assertFalse(HostSurfacePolicy.exclusive(HostSurface.TRACKER))
        assertFalse(HostSurfacePolicy.exclusive(HostSurface.CINEMA))
        assertFalse(HostSurfacePolicy.exclusive(HostSurface.THEATER))
    }

    @Test
    fun `shared HUD bodies hide on exclusive surfaces`() {
        assertTrue(HostSurfacePolicy.showsTracker(HostSurface.HUD))
        assertTrue(HostSurfacePolicy.showsTracker(HostSurface.TRACKER))
        assertTrue(HostSurfacePolicy.showsCinema(HostSurface.CINEMA))
        assertTrue(HostSurfacePolicy.showsTheater(HostSurface.THEATER))
        assertFalse(HostSurfacePolicy.showsTracker(HostSurface.SEAT))
        assertFalse(HostSurfacePolicy.showsCinema(HostSurface.HELPER))
        assertFalse(HostSurfacePolicy.showsTheater(HostSurface.COCKPIT))
    }

    @Test
    fun `cockpit blocks seat and helper`() {
        assertFalse(HostSurfacePolicy.seatAllowed(HostSurface.HUD, cockpit = true))
        assertFalse(HostSurfacePolicy.helperAllowed(HostSurface.HUD, cockpit = true))
        assertTrue(HostSurfacePolicy.seatAllowed(HostSurface.HUD, cockpit = false))
        assertTrue(HostSurfacePolicy.helperAllowed(HostSurface.HUD, cockpit = false))
        assertFalse(HostSurfacePolicy.seatAllowed(HostSurface.HELPER, cockpit = false))
        assertFalse(HostSurfacePolicy.helperAllowed(HostSurface.SEAT, cockpit = false))
    }

    @Test
    fun `applyIsNoop ignores surface so touch-claim flag is applied independently`() {
        assertTrue(
            InputOwnerPolicy.applyIsNoop(
                true, InputOwner.GAME, true,
                true, InputOwner.GAME, true,
            ),
        )
        assertTrue(HostSurfacePolicy.playHostTouchClaimEnabled(true, HostSurface.HUD))
        assertTrue(HostSurfacePolicy.playHostTouchClaimEnabled(true, HostSurface.TRACKER))
        assertFalse(HostSurfacePolicy.playHostTouchClaimEnabled(true, HostSurface.SEAT))
        assertFalse(HostSurfacePolicy.playHostTouchClaimEnabled(false, HostSurface.HUD))
        assertFalse(HostSurfacePolicy.playHostTouchClaimEnabled(false, HostSurface.SEAT))
    }

    @Test
    fun `seat chip and body never claim HOST`() {
        assertTrue(
            HostSurfacePolicy.shouldClaimPlayHostTouch(true, HostSurface.HUD, false),
        )
        assertFalse(
            HostSurfacePolicy.shouldClaimPlayHostTouch(true, HostSurface.HUD, true),
        )
        assertFalse(
            HostSurfacePolicy.shouldClaimPlayHostTouch(true, HostSurface.SEAT, false),
        )
        assertFalse(
            HostSurfacePolicy.shouldClaimPlayHostTouch(true, HostSurface.SEAT, true),
        )
        assertFalse(
            HostSurfacePolicy.shouldClaimPlayHostTouch(false, HostSurface.HUD, false),
        )
        assertTrue(CompanionPanel.isSeatChromeTag("play_hud_seat"))
        assertTrue(CompanionPanel.isSeatChromeTag("play_hud_seat_chip"))
        assertFalse(CompanionPanel.isSeatChromeTag("play_hud_pause"))
        assertFalse(CompanionPanel.isSeatChromeTag(null))
    }
}
