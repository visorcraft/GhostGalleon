package com.visorcraft.ghostgalleon.ui

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
}
