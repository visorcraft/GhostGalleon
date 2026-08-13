package com.visorcraft.ghostgalleon.rom

data class OracleTally(
    val misses: Int = 0,
    val backoffUntilMs: Long = 0L,
)

object OracleTallyLogic {
    const val LUMA_MISS = 8
    const val MISSES_TO_HEAL = 3
    const val FAIL_BACKOFF_MS = 10_000L

    fun onSample(
        tally: OracleTally,
        maxLuma: Int?,
        copyFailed: Boolean,
        nowMs: Long,
    ): Pair<OracleTally, Boolean> {
        if (nowMs < tally.backoffUntilMs) return tally to false
        if (copyFailed) {
            return OracleTally(misses = 0, backoffUntilMs = nowMs + FAIL_BACKOFF_MS) to false
        }
        val miss = maxLuma != null && maxLuma < LUMA_MISS
        if (!miss) return OracleTally(0, tally.backoffUntilMs) to false
        val n = tally.misses + 1
        return if (n >= MISSES_TO_HEAL) {
            OracleTally(0, tally.backoffUntilMs) to true
        } else {
            OracleTally(n, tally.backoffUntilMs) to false
        }
    }
}
