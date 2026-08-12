package com.visorcraft.ghostgalleon.input

/**
 * Analog-stick hysteresis from a single deadzone percent (20–80).
 * Release = deadzone/100; engage = release + 0.20 (capped at 0.90).
 * Host-tested; no Android types.
 */
object StickThresholds {

    const val DEFAULT_PERCENT = 50
    const val MIN_PERCENT = 20
    const val MAX_PERCENT = 80

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun release(percent: Int): Float = clamp(percent) / 100f

    fun engage(percent: Int): Float =
        ((clamp(percent) + 20).coerceAtMost(90)) / 100f
}
