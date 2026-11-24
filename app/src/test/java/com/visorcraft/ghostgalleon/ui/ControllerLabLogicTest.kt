package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
