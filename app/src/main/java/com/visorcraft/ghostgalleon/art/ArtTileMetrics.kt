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

    /** 1px inset inside the backing's 1dp hairline so every cover keeps uniform edge definition. */
    const val COVER_HAIRLINE_INSET_PX: Int = 1

    /** Refined physical box-art corner radius fraction & dp bounds for letterboxed covers. */
    const val LETTERBOX_CORNER_FRAC: Float = 0.45f
    const val LETTERBOX_CORNER_MIN_DP: Float = 4f
    const val LETTERBOX_CORNER_MAX_DP: Float = 10f

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

    /** Animation duration (ms) corresponding to [mode], honoring system reduced-motion. */
    fun revealDurationMs(
        mode: RevealMode,
        animationsEnabled: Boolean = true,
    ): Long = if (animationsEnabled && mode == RevealMode.FADE) ASYNC_FADE_MS else SYNC_SNAP_MS

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
     * Aspect-proportional corner radius (px) for cover artwork inside a rounded
     * dark backing well:
     * - Near-square full-bleed covers (`0.80..1.25`) preserve [outerRadiusPx]
     *   (clamped safely to `targetPx * 0.5f`).
     * - Non-square letterboxed covers (2:3 portrait, 4:3, wide banners) use
     *   refined physical box-art corners (`0.45 * outerRadiusPx`, clamped to
     *   `4..10dp` and bounded below [outerRadiusPx] and `targetPx * 0.25f`).
     */
    fun innerCoverCornerRadiusPx(
        outerRadiusPx: Float,
        bmpW: Int,
        bmpH: Int,
        targetPx: Int,
        density: Float = 2f,
    ): Float {
        val safeOuter = if (outerRadiusPx.isNaN() || outerRadiusPx <= 0f) 0f else outerRadiusPx
        if (safeOuter <= 0f) return 0f
        if (isNearSquareAspect(bmpW, bmpH)) {
            val maxForTarget = if (targetPx > 0) targetPx * 0.5f else safeOuter
            return safeOuter.coerceAtMost(maxForTarget)
        }
        val safeDensity = density.coerceAtLeast(0.5f)
        val minPx = LETTERBOX_CORNER_MIN_DP * safeDensity
        val maxPx = LETTERBOX_CORNER_MAX_DP * safeDensity
        val scaled = (safeOuter * LETTERBOX_CORNER_FRAC).coerceIn(minPx, maxPx)
        val maxForTarget = if (targetPx > 0) targetPx * 0.25f else scaled
        return scaled.coerceAtMost(safeOuter).coerceAtMost(maxForTarget)
    }

    /**
     * Adaptive cover/logo framing rule:
     * - Transparent logos always use `FIT_CENTER` with proportional inset padding
     *   and unclipped wordmark bounds.
     * - Near-square covers fill edge-to-edge (`CENTER_CROP`) inset `1px` inside
     *   the dark backing hairline so every tile shares uniform edge definition.
     * - Tall portrait (2:3), 4:3, and wide covers use `FIT_CENTER` inset `1px`
     *   with refined box-art corner rounding via [innerCoverCornerRadiusPx].
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
                insetPaddingPx = COVER_HAIRLINE_INSET_PX,
                roundBitmapCorners = !fullBleed,
            )
        }
    }
}
