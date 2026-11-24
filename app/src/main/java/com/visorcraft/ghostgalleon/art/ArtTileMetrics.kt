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

    /** Aspect-ratio window treated as near-square for clean full-bleed cover framing. */
    const val NEAR_SQUARE_MIN_RATIO: Float = 0.80f
    const val NEAR_SQUARE_MAX_RATIO: Float = 1.25f

    /** Logo fallback inset breathing room as fraction of tile size, clamped in dp. */
    const val LOGO_INSET_FRAC: Float = 0.10f
    const val LOGO_MIN_INSET_DP: Int = 4
    const val LOGO_MAX_INSET_DP: Int = 14

    enum class RevealMode {
        SNAP,
        FADE,
    }

    enum class ArtFrameKind {
        COVER_ART,
        LOGO_OVERLAY,
    }

    data class ArtFraming(
        val centerCrop: Boolean,
        val insetPaddingPx: Int,
        val roundBitmapCorners: Boolean,
    )

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

    /** True when [width]/[height] sits close enough to 1:1 for full-bleed crop without losing art. */
    fun isNearSquareAspect(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val ratio = width.toFloat() / height.toFloat()
        return ratio in NEAR_SQUARE_MIN_RATIO..NEAR_SQUARE_MAX_RATIO
    }

    /** Proportional inset padding (px) giving transparent logos clean breathing room. */
    fun logoInsetPaddingPx(targetPx: Int, density: Float): Int {
        val safeDensity = density.coerceAtLeast(0.5f)
        val targetDp = if (targetPx > 0) targetPx / safeDensity else 96f
        val insetDp = (targetDp * LOGO_INSET_FRAC)
            .toInt()
            .coerceIn(LOGO_MIN_INSET_DP, LOGO_MAX_INSET_DP)
        return (insetDp * safeDensity).toInt().coerceAtLeast(1)
    }

    /**
     * Adaptive cover/logo framing rule:
     * - Transparent logos always use `FIT_CENTER` with proportional inset padding
     *   and unclipped wordmark bounds.
     * - Near-square covers fill edge-to-edge (`CENTER_CROP`) inside the rounded
     *   outline so jewel cases / 1:1 covers never leave thin letterbox slivers.
     * - Tall portrait (2:3) and wide covers use `FIT_CENTER` over the dark
     *   hairline-framed backing so box artwork is never cropped.
     */
    fun framingFor(
        kind: ArtFrameKind,
        bitmapWidth: Int,
        bitmapHeight: Int,
        targetPx: Int,
        density: Float,
    ): ArtFraming = when (kind) {
        ArtFrameKind.LOGO_OVERLAY -> ArtFraming(
            centerCrop = false,
            insetPaddingPx = logoInsetPaddingPx(targetPx, density),
            roundBitmapCorners = false,
        )
        ArtFrameKind.COVER_ART -> {
            val fullBleed = isNearSquareAspect(bitmapWidth, bitmapHeight)
            ArtFraming(
                centerCrop = fullBleed,
                insetPaddingPx = 0,
                roundBitmapCorners = !fullBleed,
            )
        }
    }
}
