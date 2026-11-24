package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {

    @Test
    fun `effectiveDurationMs collapses to zero when reduced motion is active or duration non-positive`() {
        assertEquals(180L, MotionPolicy.effectiveDurationMs(180L, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.effectiveDurationMs(180L, animatorsEnabled = false))
        assertEquals(0L, MotionPolicy.effectiveDurationMs(0L, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.effectiveDurationMs(-50L, animatorsEnabled = true))
    }

    @Test
    fun `shouldAnimate requires enabled animators, window attachment, and positive duration`() {
        assertTrue(MotionPolicy.shouldAnimate(115L, animatorsEnabled = true, attached = true))
        assertFalse(MotionPolicy.shouldAnimate(115L, animatorsEnabled = false, attached = true))
        assertFalse(MotionPolicy.shouldAnimate(115L, animatorsEnabled = true, attached = false))
        assertFalse(MotionPolicy.shouldAnimate(0L, animatorsEnabled = true, attached = true))
    }

    @Test
    fun `settle helpers snap to resting values immediately when reduced motion is active`() {
        assertEquals(1f, MotionPolicy.initialSettleAlpha(animatorsEnabled = false, startAlpha = 0.6f), 0.001f)
        assertEquals(0.6f, MotionPolicy.initialSettleAlpha(animatorsEnabled = true, startAlpha = 0.6f), 0.001f)
        assertEquals(0f, MotionPolicy.initialSettleAlpha(animatorsEnabled = true, startAlpha = -0.2f), 0.001f)

        assertEquals(0f, MotionPolicy.initialSettleTranslationPx(false, offsetDp = 4f, density = 2f), 0.001f)
        assertEquals(8f, MotionPolicy.initialSettleTranslationPx(true, offsetDp = 4f, density = 2f), 0.001f)
        assertEquals(0f, MotionPolicy.initialSettleTranslationPx(true, offsetDp = 4f, density = 0f), 0.001f)
    }
}
