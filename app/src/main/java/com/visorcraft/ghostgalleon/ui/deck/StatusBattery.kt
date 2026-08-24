package com.visorcraft.ghostgalleon.ui.deck

/**
 * Host-tested charge chrome for the status pill. Android only supplies
 * plugged / current / charge-counter readings.
 *
 * Glyphs:
 * - [Glyph.BATTERY] unplugged (4-bar fill from percent)
 * - [Glyph.CHARGING] power connected and battery gaining (white bolt)
 * - [Glyph.NET_DRAIN] power connected but net discharge (red bolt)
 */
object StatusBattery {

    enum class Glyph { BATTERY, CHARGING, NET_DRAIN }

    /** AOSP CURRENT_NOW: negative microamps = discharging. */
    const val AOSP_DISCHARGE_SIGN = -1

    /** Ignore CURRENT_NOW below 100 mA so idle noise does not flip the bolt. */
    const val CURRENT_NOISE_UA = 100_000L

    /** Ignore charge-counter jitter under 200 µAh between samples. */
    const val COUNTER_NOISE_UAH = 200L

    /** Consecutive CHARGING ↔ NET_DRAIN samples required before swapping. */
    const val GLYPH_STABILIZE_HITS = 2

    fun percentFrom(level: Int, scale: Int, capacityProperty: Int): Int {
        if (level >= 0 && scale > 0) {
            return ((level * 100f) / scale).toInt().coerceIn(0, 100)
        }
        return if (capacityProperty in 0..100) capacityProperty else -1
    }

    fun isPlugged(plugged: Int): Boolean = plugged != 0

    /**
     * Four discrete bars. 0% is empty outline; any charge shows at least one
     * bar so a 5% pack is not drawn as dead.
     */
    fun batteryBars(percent: Int): Int = when {
        percent <= 0 -> 0
        percent <= 25 -> 1
        percent <= 50 -> 2
        percent <= 75 -> 3
        else -> 4
    }

    /**
     * Sign of a discharging CURRENT_NOW sample, or null when the reading is
     * too small to trust. Call only while unplugged.
     */
    fun learnDischargeSign(currentUa: Long, noiseUa: Long = CURRENT_NOISE_UA): Int? {
        if (currentUa == Long.MIN_VALUE) return null
        if (kotlin.math.abs(currentUa) < noiseUa) return null
        return if (currentUa > 0L) 1 else -1
    }

    /**
     * Net drain while plugged. Prefers CURRENT_NOW vs the learned discharge
     * sign; falls back to a falling charge counter when current is unknown.
     */
    fun draining(
        currentUa: Long?,
        dischargeSign: Int,
        counterDelta: Long?,
        currentNoiseUa: Long = CURRENT_NOISE_UA,
        counterNoise: Long = COUNTER_NOISE_UAH,
    ): Boolean {
        val fromCurrent = currentIndicatesDrain(currentUa, dischargeSign, currentNoiseUa)
        if (fromCurrent != null) return fromCurrent
        return counterDelta != null && counterDelta <= -counterNoise
    }

    /**
     * [percent] 100 while plugged is always charging (trickle at full
     * must not show the red drain bolt).
     */
    fun glyph(plugged: Boolean, draining: Boolean, percent: Int = -1): Glyph = when {
        !plugged -> Glyph.BATTERY
        percent >= 100 -> Glyph.CHARGING
        draining -> Glyph.NET_DRAIN
        else -> Glyph.CHARGING
    }

    /**
     * Plug/unplug swaps immediately. CHARGING ↔ NET_DRAIN waits for
     * [needed] matching candidates so noisy CURRENT_NOW does not flicker.
     */
    fun stabilize(
        shown: Glyph,
        candidate: Glyph,
        pending: Glyph?,
        hits: Int,
        needed: Int = GLYPH_STABILIZE_HITS,
    ): Triple<Glyph, Glyph?, Int> {
        if (candidate == shown) return Triple(shown, null, 0)
        if (shown == Glyph.BATTERY || candidate == Glyph.BATTERY) {
            return Triple(candidate, null, 0)
        }
        val nextHits = if (pending == candidate) hits + 1 else 1
        return if (nextHits >= needed) Triple(candidate, null, 0)
        else Triple(shown, candidate, nextHits)
    }

    fun chromeNeedsWrite(
        previousPercent: Int,
        previousGlyph: Glyph,
        previousBars: Int,
        nextPercent: Int,
        nextGlyph: Glyph,
        nextBars: Int,
    ): Boolean {
        if (previousPercent != nextPercent || previousGlyph != nextGlyph) return true
        return nextGlyph == Glyph.BATTERY && previousBars != nextBars
    }

    private fun currentIndicatesDrain(
        currentUa: Long?,
        dischargeSign: Int,
        noiseUa: Long,
    ): Boolean? {
        val ua = currentUa ?: return null
        if (ua == Long.MIN_VALUE) return null
        if (kotlin.math.abs(ua) < noiseUa) return null
        val sign = if (ua > 0L) 1 else -1
        return sign == dischargeSign
    }
}
