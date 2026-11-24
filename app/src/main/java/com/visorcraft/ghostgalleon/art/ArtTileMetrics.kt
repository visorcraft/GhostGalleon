package com.visorcraft.ghostgalleon.art

/**
 * Pure, host-tested framing and crossfade transition policy for [ArtTile].
 * Keeps memory-cache hits instantaneous (zero scroll flicker) while smoothing
 * asynchronous disk/decode cover arrivals over a hairline-framed dark backing.
 */
object ArtTileMetrics {

    /** Smooth crossfade duration (ms) when cover art arrives asynchronously. */
    const val ASYNC_FADE_MS: Long = 140L

    /** Instant reveal duration (ms) for synchronous memory-cache hits. */
    const val SYNC_SNAP_MS: Long = 0L

    /** Minimum hairline stroke width (px) on cover art sunken backing. */
    const val MIN_BACKING_STROKE_PX: Int = 1

    enum class RevealMode {
        SNAP,
        FADE,
    }

    /**
     * Decide whether incoming cover artwork should snap immediately or crossfade.
     * - Synchronous memory-cache hits always [RevealMode.SNAP] (never flicker on scroll).
     * - Pre-attach binds always [RevealMode.SNAP] so initial layout paints cleanly.
     * - Attached async decodes [RevealMode.FADE] smoothly over the dark backing.
     */
    fun revealMode(wasSyncMemoryHit: Boolean, isAttached: Boolean): RevealMode =
        if (!wasSyncMemoryHit && isAttached) RevealMode.FADE else RevealMode.SNAP

    /** Animation duration (ms) corresponding to [mode]. */
    fun revealDurationMs(mode: RevealMode): Long =
        if (mode == RevealMode.FADE) ASYNC_FADE_MS else SYNC_SNAP_MS

    /**
     * Clamp rounded outline radius (px) so small dock/strip tiles never exceed
     * half their target dimension while standard cards keep exact theme radius.
     */
    fun clampedCornerRadiusPx(
        cornerRadiusDp: Int,
        density: Float,
        targetPx: Int,
    ): Float {
        val safeDensity = density.coerceAtLeast(0.1f)
        val rawPx = cornerRadiusDp.coerceAtLeast(0) * safeDensity
        if (targetPx <= 0) return rawPx
        return rawPx.coerceAtMost(targetPx * 0.5f)
    }

    /** Subtle 1dp hairline border width (px) for sunken art backing. */
    fun backingStrokePx(density: Float): Int =
        (1f * density.coerceAtLeast(0.1f)).toInt().coerceAtLeast(MIN_BACKING_STROKE_PX)
}
