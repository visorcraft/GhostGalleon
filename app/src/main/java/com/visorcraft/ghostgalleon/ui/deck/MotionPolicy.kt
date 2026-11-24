package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure reduced-motion and animation-lifecycle gating policy shared across
 * CompanionPanel, StatusPill, IconButton, and BaseDeckActivity chrome.
 * Host-tested.
 */
object MotionPolicy {

    /**
     * Resolve effective animator duration (ms). Returns `0L` whenever system
     * animators are disabled (reduced-motion / accessibility) or [requestedMs] <= 0.
     */
    fun effectiveDurationMs(requestedMs: Long, animatorsEnabled: Boolean): Long =
        if (!animatorsEnabled || requestedMs <= 0L) 0L else requestedMs

    /**
     * True only when an animation should actually run on-screen: animators are
     * enabled, the target view is attached to a window, and duration is positive.
     */
    fun shouldAnimate(
        requestedMs: Long,
        animatorsEnabled: Boolean,
        attached: Boolean,
    ): Boolean = attached && effectiveDurationMs(requestedMs, animatorsEnabled) > 0L

    /** Initial alpha for settle transitions (`1f` immediately under reduced motion). */
    fun initialSettleAlpha(animatorsEnabled: Boolean, startAlpha: Float): Float =
        if (animatorsEnabled) startAlpha.coerceIn(0f, 1f) else 1f

    /** Initial vertical settle offset in px (`0f` immediately under reduced motion). */
    fun initialSettleTranslationPx(
        animatorsEnabled: Boolean,
        offsetDp: Float,
        density: Float,
    ): Float = if (animatorsEnabled && density > 0f) offsetDp * density else 0f
}
