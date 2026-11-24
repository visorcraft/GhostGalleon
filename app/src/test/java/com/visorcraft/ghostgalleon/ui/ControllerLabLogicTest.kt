package com.visorcraft.ghostgalleon.ui

import android.view.KeyEvent
import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerLabLogicTest {

    @Test
    fun `axisBarSpan maps center, positive, negative, and invalid values cleanly`() {
        val zero = ControllerLabLogic.axisBarSpan(0f)
        assertEquals(0.5f, zero.startFrac, 0.0001f)
        assertEquals(0f, zero.widthFrac, 0.0001f)
        assertEquals(0f, zero.clampedValue, 0.0001f)

        val fullPos = ControllerLabLogic.axisBarSpan(1.5f)
        assertEquals(0.5f, fullPos.startFrac, 0.0001f)
        assertEquals(0.5f, fullPos.widthFrac, 0.0001f)
        assertEquals(1f, fullPos.clampedValue, 0.0001f)

        val halfNeg = ControllerLabLogic.axisBarSpan(-0.5f)
        assertEquals(0.25f, halfNeg.startFrac, 0.0001f)
        assertEquals(0.25f, halfNeg.widthFrac, 0.0001f)
        assertEquals(-0.5f, halfNeg.clampedValue, 0.0001f)

        val fullNeg = ControllerLabLogic.axisBarSpan(-2f)
        assertEquals(0f, fullNeg.startFrac, 0.0001f)
        assertEquals(0.5f, fullNeg.widthFrac, 0.0001f)
        assertEquals(-1f, fullNeg.clampedValue, 0.0001f)

        val nan = ControllerLabLogic.axisBarSpan(Float.NaN)
        assertEquals(0.5f, nan.startFrac, 0.0001f)
        assertEquals(0f, nan.widthFrac, 0.0001f)
    }

    @Test
    fun `mappedAction resolves bound and unbound keycodes`() {
        val map = mapOf(96 to Action.CONFIRM, 97 to Action.BACK)
        assertEquals(Action.CONFIRM, ControllerLabLogic.mappedAction(96, map))
        assertEquals(Action.BACK, ControllerLabLogic.mappedAction(97, map))
        assertNull(ControllerLabLogic.mappedAction(99, map))
    }

    @Test
    fun `deadzoneMarkers map release and engage hysteresis symmetrically around center`() {
        // Default 50%: release = 0.50 -> (0.25, 0.75); engage = 0.70 -> (0.15, 0.85).
        val def = ControllerLabLogic.deadzoneMarkers(50)
        assertEquals(0.25f, def.releaseFracNeg, 0.0001f)
        assertEquals(0.75f, def.releaseFracPos, 0.0001f)
        assertEquals(0.15f, def.engageFracNeg, 0.0001f)
        assertEquals(0.85f, def.engageFracPos, 0.0001f)

        // Clamped extremes remain symmetric and engage stays outside release.
        for (pct in listOf(0, 20, 45, 80, 100)) {
            val m = ControllerLabLogic.deadzoneMarkers(pct)
            assertEquals(1f, m.releaseFracNeg + m.releaseFracPos, 0.0001f)
            assertEquals(1f, m.engageFracNeg + m.engageFracPos, 0.0001f)
            assertTrue(m.engageFracNeg < m.releaseFracNeg)
            assertTrue(m.engageFracPos > m.releaseFracPos)
        }
    }

    @Test
    fun `updatePeaks tracks extreme positive and negative deflections and clamps invalid inputs`() {
        var peaks = AxisPeakState()
        peaks = ControllerLabLogic.updatePeaks(peaks, 0.4f)
        assertEquals(0f, peaks.minNeg, 0.0001f)
        assertEquals(0.4f, peaks.maxPos, 0.0001f)

        peaks = ControllerLabLogic.updatePeaks(peaks, -0.82f)
        assertEquals(-0.82f, peaks.minNeg, 0.0001f)
        assertEquals(0.4f, peaks.maxPos, 0.0001f)

        peaks = ControllerLabLogic.updatePeaks(peaks, 0.2f)
        assertEquals(-0.82f, peaks.minNeg, 0.0001f)
        assertEquals(0.4f, peaks.maxPos, 0.0001f)

        peaks = ControllerLabLogic.updatePeaks(peaks, Float.NaN)
        assertEquals(-0.82f, peaks.minNeg, 0.0001f)
        assertEquals(0.4f, peaks.maxPos, 0.0001f)

        peaks = ControllerLabLogic.updatePeaks(peaks, 2.5f)
        assertEquals(1f, peaks.maxPos, 0.0001f)
        assertEquals(1f, ControllerLabLogic.valueToBarFrac(1f), 0.0001f)
        assertEquals(0f, ControllerLabLogic.valueToBarFrac(-1f), 0.0001f)
        assertEquals(0.5f, ControllerLabLogic.valueToBarFrac(0f), 0.0001f)
    }

    @Test
    fun `padIndicator lookup maps standard buttons and HAT directions`() {
        assertEquals(PadIndicator.A, ControllerLabLogic.padIndicatorForKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(PadIndicator.L1, ControllerLabLogic.padIndicatorForKeyCode(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(PadIndicator.DPAD_UP, ControllerLabLogic.padIndicatorForKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(ControllerLabLogic.padIndicatorForKeyCode(KeyEvent.KEYCODE_VOLUME_UP))

        assertTrue(ControllerLabLogic.padIndicatorsForHat(0f, 0f).isEmpty())
        assertEquals(
            setOf(PadIndicator.DPAD_LEFT, PadIndicator.DPAD_UP),
            ControllerLabLogic.padIndicatorsForHat(-1f, -1f),
        )
        assertEquals(
            setOf(PadIndicator.DPAD_RIGHT, PadIndicator.DPAD_DOWN),
            ControllerLabLogic.padIndicatorsForHat(0.8f, 0.9f),
        )
    }
}
