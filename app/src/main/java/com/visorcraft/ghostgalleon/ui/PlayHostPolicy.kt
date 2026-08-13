package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.rom.SessionPolicy

object PlayHostPolicy {
    fun playHostAllowed(
        dualMode: Boolean,
        policy: SessionPolicy?,
        greedy: Boolean,
        hostDisplayId: Int?,
        launchDisplayId: Int?,
    ): Boolean {
        if (!dualMode) return false
        if (policy != SessionPolicy.KEEP_COMPANION) return false
        if (greedy) return false
        if (hostDisplayId == null || launchDisplayId == null) return false
        return hostDisplayId != launchDisplayId
    }

    fun oracleMaySample(
        dualMode: Boolean,
        ownsCompanionDisplay: Boolean,
        windowDisplayId: Int?,
        launchDisplayId: Int?,
        sessionOpen: Boolean,
    ): Boolean {
        if (!dualMode) return false
        if (ownsCompanionDisplay) return false
        if (windowDisplayId == null) return false
        if (sessionOpen && windowDisplayId == launchDisplayId) return false
        return true
    }

    /**
     * Arm PixelCopy only on the companion surface, and only when the user
     * left detect-black on. Primary must not sample its own carousel.
     */
    fun oracleShouldSchedule(detectEnabled: Boolean, companionSurface: Boolean): Boolean =
        detectEnabled && companionSurface

    /** Skip TextView writes when the minute-granularity clock string is unchanged. */
    fun playHudClockNeedsWrite(previous: CharSequence?, next: String): Boolean =
        previous?.toString() != next

    /** Ms until [elapsedMs] crosses the next whole minute. */
    fun playHudClockDelayMs(elapsedMs: Long): Long {
        val minute = 60_000L
        val rem = ((elapsedMs % minute) + minute) % minute
        return (minute - rem).coerceIn(1_000L, minute)
    }

    /** Clock is minute-granular; RA probe is slower than 1 Hz when watching. */
    fun playHudTickDelayMs(elapsedMs: Long, watchRa: Boolean, raProbeMs: Long): Long {
        val clock = playHudClockDelayMs(elapsedMs)
        if (!watchRa) return clock
        return minOf(clock, raProbeMs.coerceAtLeast(1_000L))
    }
}
