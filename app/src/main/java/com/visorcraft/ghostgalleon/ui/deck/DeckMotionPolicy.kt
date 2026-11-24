package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ValueAnimator
import android.os.Build

/**
 * Reduced-motion animation gate for Track A surfaces (`GridDeck`, `DockBar`,
 * `FolderPanel`, `SystemChromeIcons`).
 *
 * Pure boolean predicates stay host-tested without Android framework stubs;
 * [areSystemAnimatorsEnabled] reads the system animator duration scale safely.
 */
object DeckMotionPolicy {

    /** True when transient transitions (focus scale, pill morph, bump, press) may run. */
    fun shouldAnimate(animatorsEnabled: Boolean, isLaidOut: Boolean = true): Boolean =
        animatorsEnabled && isLaidOut

    /** True when continuous breathing/pulse loops (move-mode lift & banner dot) may run. */
    fun shouldPulse(animatorsEnabled: Boolean): Boolean = animatorsEnabled

    /** Query Android's global animator-enabled setting (respects "Remove animations"). */
    fun areSystemAnimatorsEnabled(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            true
        }
    } catch (_: Throwable) {
        true
    }
}
