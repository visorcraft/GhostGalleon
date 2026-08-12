package com.visorcraft.ghostgalleon.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickThresholdsTest {

    @Test
    fun `default is 0_50 release and 0_70 engage`() {
        assertEquals(0.50f, StickThresholds.release(50), 0.001f)
        assertEquals(0.70f, StickThresholds.engage(50), 0.001f)
    }

    @Test
    fun `lower deadzone tightens both thresholds`() {
        assertEquals(0.30f, StickThresholds.release(30), 0.001f)
        assertEquals(0.50f, StickThresholds.engage(30), 0.001f)
    }

    @Test
    fun `clamps and keeps engage above release`() {
        assertEquals(0.20f, StickThresholds.release(5), 0.001f)
        assertEquals(0.80f, StickThresholds.release(99), 0.001f)
        assertEquals(0.90f, StickThresholds.engage(80), 0.001f)
        assertTrue(StickThresholds.engage(80) > StickThresholds.release(80))
        assertTrue(StickThresholds.engage(20) > StickThresholds.release(20))
    }
}
