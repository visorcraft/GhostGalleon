package com.visorcraft.ghostgalleon.rom

data class CinemaFrame(val slot: Int, val savedAtMs: Long, val thumbKey: String?)

object CinemaPolicy {
    val USER_SLOTS: IntRange = 1..8
    val BAND: IntRange = 9..12
    const val DEFAULT_INTERVAL_MS = 60_000L
    const val MIN_INTERVAL_MS = 15_000L
    const val MAX_INTERVAL_MS = 300_000L

    fun nextSlot(lastSlot: Int?): Int {
        if (lastSlot == null || lastSlot !in BAND) return BAND.first
        return if (lastSlot >= BAND.last) BAND.first else lastSlot + 1
    }

    fun inBand(slot: Int): Boolean = slot in BAND

    fun clampInterval(ms: Long): Long = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

    fun shouldCapture(
        enabled: Boolean,
        playHostAllowed: Boolean,
        raPlayer: Boolean,
        slotsLive: Boolean,
        lastCaptureMs: Long,
        nowMs: Long,
        intervalMs: Long,
    ): Boolean {
        if (!enabled || !playHostAllowed || !raPlayer || !slotsLive) return false
        val wait = clampInterval(intervalMs)
        if (lastCaptureMs <= 0L) return true
        return nowMs - lastCaptureMs >= wait
    }
}
