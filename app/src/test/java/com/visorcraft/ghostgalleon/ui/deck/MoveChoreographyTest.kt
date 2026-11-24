package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveChoreographyTest {

    @Test
    fun `resolveRole prioritizes move mode over ordinary focus`() {
        assertEquals(
            MoveChoreography.SlotRole.LIFTED,
            MoveChoreography.resolveRole(position = 3, selectedIndex = 3, movingIndex = 3, surfaceFocused = true),
        )
        assertEquals(
            MoveChoreography.SlotRole.DROP_TARGET,
            MoveChoreography.resolveRole(position = 1, selectedIndex = 3, movingIndex = 3, surfaceFocused = true),
        )
        // Normal mode when movingIndex is null
        assertEquals(
            MoveChoreography.SlotRole.FOCUSED,
            MoveChoreography.resolveRole(position = 2, selectedIndex = 2, movingIndex = null, surfaceFocused = true),
        )
        assertEquals(
            MoveChoreography.SlotRole.NORMAL,
            MoveChoreography.resolveRole(position = 2, selectedIndex = 2, movingIndex = null, surfaceFocused = false),
        )
    }

    @Test
    fun `lifted slot stands strictly higher than focused, normal, and drop-target slots`() {
        val lifted = MoveChoreography.gridVisualSpec(MoveChoreography.SlotRole.LIFTED, isBlank = false)
        val focused = MoveChoreography.gridVisualSpec(MoveChoreography.SlotRole.FOCUSED, isBlank = false)
        val normal = MoveChoreography.gridVisualSpec(MoveChoreography.SlotRole.NORMAL, isBlank = false)
        val dropFilled = MoveChoreography.gridVisualSpec(MoveChoreography.SlotRole.DROP_TARGET, isBlank = false)
        val dropBlank = MoveChoreography.gridVisualSpec(MoveChoreography.SlotRole.DROP_TARGET, isBlank = true)

        assertTrue(lifted.scale > focused.scale)
        assertTrue(focused.scale > normal.scale)
        assertTrue(normal.scale > dropFilled.scale)
        assertTrue(lifted.elevationDp > focused.elevationDp)
        assertTrue(focused.elevationDp > normal.elevationDp)
        assertEquals(0f, dropFilled.elevationDp, 0.001f)
        assertTrue(dropFilled.dashedDropOutline)
        assertTrue(dropBlank.dashedDropOutline)
        assertFalse(lifted.dashedDropOutline)
        assertTrue(dropBlank.alpha > dropFilled.alpha)

        val dockLifted = MoveChoreography.dockVisualSpec(MoveChoreography.SlotRole.LIFTED, isBlank = false)
        val dockFocused = MoveChoreography.dockVisualSpec(MoveChoreography.SlotRole.FOCUSED, isBlank = false)
        val dockDrop = MoveChoreography.dockVisualSpec(MoveChoreography.SlotRole.DROP_TARGET, isBlank = false)
        assertTrue(dockLifted.scale > dockFocused.scale)
        assertTrue(dockLifted.elevationDp > dockFocused.elevationDp)
        assertTrue(dockDrop.dashedDropOutline)
    }

    @Test
    fun `lifted border meets WCAG AA contrast across all built-in themes and dark custom accents`() {
        val accents = ThemePack.BUILTINS.map { it.accentColor } + listOf(
            0xFF1A237E.toInt(), // deep navy
            0xFF4A148C.toInt(), // deep purple
            0xFFFFFFFF.toInt(), // pure white
        )
        for (tokens in ThemePack.BUILTINS) {
            val fill = ThemePack.cardFill(tokens)
            for (accent in accents) {
                val border = MoveChoreography.liftedBorderColor(accent, fill)
                val ratio = ThemePack.contrastRatio(border, fill)
                assertTrue(
                    "theme=${tokens.id} accent=${accent.toUInt().toString(16)} ratio=$ratio < 4.5",
                    ratio >= ThemePack.MIN_TEXT_CONTRAST,
                )
                val wash = MoveChoreography.liftedWashFill(accent, fill)
                assertEquals(0xFF, (wash ushr 24) and 0xFF)
                val dropStroke = MoveChoreography.dropTargetStrokeColor(accent, fill)
                assertEquals(0x85, (dropStroke ushr 24) and 0xFF)
            }
        }
    }

    @Test
    fun `blendRgb endpoints and midpoint work cleanly`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals(black, MoveChoreography.blendRgb(black, white, 0f))
        assertEquals(white, MoveChoreography.blendRgb(black, white, 1f))
        val mid = MoveChoreography.blendRgb(black, white, 0.5f)
        assertEquals(128, (mid shr 16) and 0xFF)
        assertEquals(128, (mid shr 8) and 0xFF)
        assertEquals(128, mid and 0xFF)
    }

    @Test
    fun `gridSettleMotion applies subtle overshoot spring on focus gain and crisp decelerate on loss`() {
        val gain = MoveChoreography.gridSettleMotion(MoveChoreography.SlotRole.FOCUSED)
        val loss = MoveChoreography.gridSettleMotion(MoveChoreography.SlotRole.NORMAL)
        val drop = MoveChoreography.gridSettleMotion(MoveChoreography.SlotRole.DROP_TARGET)

        assertEquals(110L, gain.durationMs)
        assertEquals(MoveChoreography.MotionCurve.OVERSHOOT, gain.curve)
        assertEquals(0.9f, gain.overshootTension, 0.0001f)

        assertEquals(80L, loss.durationMs)
        assertEquals(MoveChoreography.MotionCurve.DECELERATE, loss.curve)
        assertEquals(0f, loss.overshootTension, 0.0001f)

        assertEquals(MoveChoreography.SETTLE_DURATION_MS, drop.durationMs)
        assertEquals(MoveChoreography.MotionCurve.DECELERATE, drop.curve)
        assertTrue("focus gain duration should exceed focus loss", gain.durationMs > loss.durationMs)
    }
}
