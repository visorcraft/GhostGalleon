package com.visorcraft.ghostgalleon.ui

enum class HostSurface { HUD, TRACKER, CINEMA, THEATER, SEAT, HELPER, COCKPIT }

object HostSurfacePolicy {
    fun exclusive(surface: HostSurface): Boolean =
        surface == HostSurface.SEAT ||
            surface == HostSurface.HELPER ||
            surface == HostSurface.COCKPIT

    fun showsTracker(surface: HostSurface): Boolean = !exclusive(surface)
    fun showsCinema(surface: HostSurface): Boolean = !exclusive(surface)
    fun showsTheater(surface: HostSurface): Boolean = !exclusive(surface)

    fun seatAllowed(surface: HostSurface, cockpit: Boolean): Boolean {
        if (cockpit) return false
        return surface != HostSurface.HELPER
    }

    fun helperAllowed(surface: HostSurface, cockpit: Boolean): Boolean {
        if (cockpit) return false
        return surface != HostSurface.SEAT
    }

    /**
     * Play-host touches may claim HOST only off SEAT and HELPER.
     * Independent of [com.visorcraft.ghostgalleon.input.InputOwnerPolicy.applyIsNoop]
     * (that triple ignores [hostSurface]).
     */
    fun playHostTouchClaimEnabled(
        playHostAllowed: Boolean,
        hostSurface: HostSurface,
    ): Boolean = playHostAllowed &&
        hostSurface != HostSurface.SEAT &&
        hostSurface != HostSurface.HELPER

    fun shouldClaimPlayHostTouch(
        playHostAllowed: Boolean,
        hostSurface: HostSurface,
        downTargetIsSeatChrome: Boolean,
    ): Boolean = playHostTouchClaimEnabled(playHostAllowed, hostSurface) &&
        !downTargetIsSeatChrome
}
