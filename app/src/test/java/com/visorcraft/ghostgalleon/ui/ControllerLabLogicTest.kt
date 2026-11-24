package com.visorcraft.ghostgalleon.ui

import android.view.KeyEvent
import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.EnumSet
import java.util.Locale

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

    @Test
    fun `compact height detection tightens log box height on 540dp panels`() {
        assertTrue(ControllerLabLogic.isCompactHeight(540))
        assertFalse(ControllerLabLogic.isCompactHeight(720))
        assertTrue(ControllerLabLogic.logBoxHeightDp(540) < ControllerLabLogic.logBoxHeightDp(720))
    }

    @Test
    fun `quantized axis deduplication ignores sub-hundredth noise and formats identically to printf`() {
        // Sub-0.005 jitter around zero collapses to canonical 0 ("+0.00") without triggering updates.
        assertEquals(0, ControllerLabLogic.quantizeAxisHundredths(0f))
        assertEquals(0, ControllerLabLogic.quantizeAxisHundredths(-0f))
        assertEquals(0, ControllerLabLogic.quantizeAxisHundredths(0.004f))
        assertEquals(0, ControllerLabLogic.quantizeAxisHundredths(-0.004f))
        assertEquals(0, ControllerLabLogic.quantizeAxisHundredths(Float.NaN))
        assertEquals(0, ControllerLabLogic.quantizeAxisHundredths(Float.POSITIVE_INFINITY))
        assertFalse(ControllerLabLogic.axisReadoutChanged(0, 0.004f))
        assertFalse(ControllerLabLogic.axisReadoutChanged(0, -0.004f))

        // Real 0.01+ steps are detected and clamped cleanly.
        assertTrue(ControllerLabLogic.axisReadoutChanged(0, 0.01f))
        assertEquals(1, ControllerLabLogic.quantizeAxisHundredths(0.01f))
        assertEquals(-1, ControllerLabLogic.quantizeAxisHundredths(-0.01f))
        assertEquals(50, ControllerLabLogic.quantizeAxisHundredths(0.504f))
        assertFalse(ControllerLabLogic.axisReadoutChanged(50, 0.503f))
        assertTrue(ControllerLabLogic.axisReadoutChanged(50, 0.506f))
        assertEquals(100, ControllerLabLogic.quantizeAxisHundredths(1.8f))
        assertEquals(-100, ControllerLabLogic.quantizeAxisHundredths(-2.5f))

        // Every quantized value in -100..100 matches String.format(Locale.US, "%+.2f", v).
        for (q in -100..100) {
            val expected = String.format(Locale.US, "%+.2f", q * 0.01f)
            assertEquals("Mismatch at q=$q", expected, ControllerLabLogic.formatQuantizedAxis(q))
        }
    }

    @Test
    fun `fillPadIndicatorsForHat reuses caller buffer cleanly across HAT transitions`() {
        val buf = EnumSet.noneOf(PadIndicator::class.java)
        ControllerLabLogic.fillPadIndicatorsForHat(-1f, -1f, buf)
        assertEquals(setOf(PadIndicator.DPAD_LEFT, PadIndicator.DPAD_UP), buf)

        ControllerLabLogic.fillPadIndicatorsForHat(0.9f, 0f, buf)
        assertEquals(setOf(PadIndicator.DPAD_RIGHT), buf)

        ControllerLabLogic.fillPadIndicatorsForHat(0f, 0.75f, buf)
        assertEquals(setOf(PadIndicator.DPAD_DOWN), buf)

        ControllerLabLogic.fillPadIndicatorsForHat(0f, 0f, buf)
        assertTrue(buf.isEmpty())
    }

    @Test
    fun `stickRadarPoint, isStickEngaged, and radarSizeDp map 2D stick geometry cleanly`() {
        val center = ControllerLabLogic.stickRadarPoint(0f, 0f, cx = 50f, cy = 50f, r = 40f)
        assertEquals(50f, center.xPx, 0.001f)
        assertEquals(50f, center.yPx, 0.001f)

        val edge = ControllerLabLogic.stickRadarPoint(1.5f, -1.5f, cx = 50f, cy = 50f, r = 40f)
        assertEquals(90f, edge.xPx, 0.001f)
        assertEquals(10f, edge.yPx, 0.001f)

        val nanPt = ControllerLabLogic.stickRadarPoint(Float.NaN, Float.POSITIVE_INFINITY, cx = 50f, cy = 50f, r = 40f)
        assertEquals(50f, nanPt.xPx, 0.001f)
        assertEquals(50f, nanPt.yPx, 0.001f)

        assertFalse(ControllerLabLogic.isStickEngaged(0.3f, -0.4f, engage = 0.7f))
        assertTrue(ControllerLabLogic.isStickEngaged(0.75f, 0f, engage = 0.7f))
        assertTrue(ControllerLabLogic.isStickEngaged(0f, -0.7f, engage = 0.7f))

        assertEquals(88, ControllerLabLogic.radarSizeDp(540))
        assertEquals(108, ControllerLabLogic.radarSizeDp(720))
    }

    @Test
    fun `analog trigger clamping, fallback, quantization, and threshold detection work cleanly`() {
        assertEquals(0.75f, ControllerLabLogic.triggerValue(primary = 0.75f, fallback = 0.1f), 0.001f)
        assertEquals(0.82f, ControllerLabLogic.triggerValue(primary = 0f, fallback = 0.82f), 0.001f)
        assertEquals(0f, ControllerLabLogic.triggerValue(primary = -0.5f, fallback = Float.NaN), 0.001f)
        assertEquals(1f, ControllerLabLogic.triggerValue(primary = 1.8f, fallback = 0f), 0.001f)

        assertEquals(0, ControllerLabLogic.quantizeTriggerHundredths(-0.2f))
        assertEquals(0, ControllerLabLogic.quantizeTriggerHundredths(Float.NaN))
        assertEquals(50, ControllerLabLogic.quantizeTriggerHundredths(0.504f))
        assertEquals(100, ControllerLabLogic.quantizeTriggerHundredths(1.4f))
        assertEquals("0.00", ControllerLabLogic.formatQuantizedTrigger(0))
        assertEquals("0.50", ControllerLabLogic.formatQuantizedTrigger(50))
        assertEquals("1.00", ControllerLabLogic.formatQuantizedTrigger(100))

        assertFalse(ControllerLabLogic.isTriggerPressed(0.49f))
        assertTrue(ControllerLabLogic.isTriggerPressed(0.50f))
        assertTrue(ControllerLabLogic.isTriggerPressed(0.95f))
        assertFalse(ControllerLabLogic.isTriggerPressed(0.50f, threshold = 0.75f))
    }

    @Test
    fun `L3 R3 indicators, 16-button padCoverage, axisEngaged, bindButtonAlpha, and wide dashboard contract`() {
        assertEquals(16, PadIndicator.entries.size)
        assertEquals(PadIndicator.L3, ControllerLabLogic.padIndicatorForKeyCode(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertEquals(PadIndicator.R3, ControllerLabLogic.padIndicatorForKeyCode(KeyEvent.KEYCODE_BUTTON_THUMBR))

        val zeroCov = ControllerLabLogic.padCoverage(0, 16)
        assertEquals(0, zeroCov.seen)
        assertEquals(16, zeroCov.total)
        assertFalse(zeroCov.complete)

        val partialCov = ControllerLabLogic.padCoverage(9, 16)
        assertEquals(9, partialCov.seen)
        assertFalse(partialCov.complete)

        val fullCov = ControllerLabLogic.padCoverage(16, 16)
        assertEquals(16, fullCov.seen)
        assertTrue(fullCov.complete)

        val overflowCov = ControllerLabLogic.padCoverage(22, 16)
        assertEquals(16, overflowCov.seen)
        assertTrue(overflowCov.complete)

        // Default 50% deadzone -> engage threshold 0.70.
        assertFalse(ControllerLabLogic.axisEngaged(0.69f, deadzonePercent = 50))
        assertTrue(ControllerLabLogic.axisEngaged(0.70f, deadzonePercent = 50))
        assertTrue(ControllerLabLogic.axisEngaged(-0.75f, deadzonePercent = 50))
        // Lower 20% deadzone -> engage threshold 0.40.
        assertTrue(ControllerLabLogic.axisEngaged(0.42f, deadzonePercent = 20))

        assertEquals(0.55f, ControllerLabLogic.bindButtonAlpha(hasLastKey = false), 0.001f)
        assertEquals(1f, ControllerLabLogic.bindButtonAlpha(hasLastKey = true), 0.001f)

        assertTrue(ControllerLabLogic.CLEAR_BUTTON_MIN_HEIGHT_DP >= 44)
        assertFalse(ControllerLabLogic.useTwoColumnDashboard(360))
        assertFalse(ControllerLabLogic.useTwoColumnDashboard(540))
        assertTrue(ControllerLabLogic.useTwoColumnDashboard(720))
        assertTrue(ControllerLabLogic.useTwoColumnDashboard(960))
    }
}
