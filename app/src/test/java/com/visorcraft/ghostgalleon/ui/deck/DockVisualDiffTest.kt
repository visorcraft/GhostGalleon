package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockVisualDiffTest {

    private fun slotState(
        index: Int,
        focused: Int?,
        moving: Int? = null,
        slotKey: String? = if (index < 8) "com.example.app$index" else null,
        accent: Int = ThemePack.GHOST.accentColor,
        surfaceFill: Int = ThemePack.DEFAULT_CARD_FILL,
        visibleTotal: Int = 9,
    ): DockSlotVisualState {
        val role = MoveChoreography.resolveRole(
            position = index,
            selectedIndex = focused,
            movingIndex = moving,
            surfaceFocused = focused != null,
        )
        return DockSlotVisualState(
            index = index,
            role = role,
            slotKey = slotKey,
            isBlank = slotKey == null,
            accent = accent,
            surfaceFill = surfaceFill,
            visibleTotal = visibleTotal,
        )
    }

    @Test
    fun `D-pad walk across 9-slot dock mutates at most 2 slots per tick`() {
        val total = 9
        val painted = arrayOfNulls<DockSlotVisualState>(total)

        // Initial build paints all 9 slots (unfocused).
        var initialMutations = 0
        for (i in 0 until total) {
            val next = slotState(i, focused = null)
            if (DockVisualDiff.needsRepaint(painted[i], next)) {
                painted[i] = next
                initialMutations++
            }
        }
        assertEquals(total, initialMutations)

        // Entering dock focus at slot 0 mutates only slot 0 (1 mutation).
        var enterMutations = 0
        for (i in 0 until total) {
            val next = slotState(i, focused = 0)
            if (DockVisualDiff.needsRepaint(painted[i], next)) {
                painted[i] = next
                enterMutations++
            }
        }
        assertEquals(1, enterMutations)

        // Walking across all 9 slots mutates strictly 2 slots per tick.
        for (nextFocus in 1 until total) {
            var stepMutations = 0
            for (i in 0 until total) {
                val next = slotState(i, focused = nextFocus)
                if (DockVisualDiff.needsRepaint(painted[i], next)) {
                    painted[i] = next
                    stepMutations++
                }
            }
            assertEquals("Expected 2 mutated dock slots moving to $nextFocus", 2, stepMutations)
        }

        // Exiting dock focus mutates only the single previously focused slot.
        var exitMutations = 0
        for (i in 0 until total) {
            val next = slotState(i, focused = null)
            if (DockVisualDiff.needsRepaint(painted[i], next)) {
                painted[i] = next
                exitMutations++
            }
        }
        assertEquals(1, exitMutations)
    }

    @Test
    fun `dock move mode repaints all slots on entry and exit and invalidates on key or theme change`() {
        val total = 6
        val keys = MutableList<String?>(total) { if (it < 5) "com.app.$it" else null }
        val painted = Array(total) { i -> slotState(i, focused = 2, slotKey = keys[i], visibleTotal = total) }

        // Entering dock move repaints all 6 slots (1 LIFTED + 5 DROP_TARGET).
        var enterMove = 0
        for (i in 0 until total) {
            val next = slotState(i, focused = 2, moving = 2, slotKey = keys[i], visibleTotal = total)
            if (DockVisualDiff.needsRepaint(painted[i], next)) {
                painted[i] = next
                enterMove++
            }
        }
        assertEquals(total, enterMove)

        // Swapping slots 2 <-> 3 repaints only those 2 slots.
        val tmp = keys[2]
        keys[2] = keys[3]
        keys[3] = tmp
        var swapMove = 0
        for (i in 0 until total) {
            val next = slotState(i, focused = 3, moving = 3, slotKey = keys[i], visibleTotal = total)
            if (DockVisualDiff.needsRepaint(painted[i], next)) {
                painted[i] = next
                swapMove++
            }
        }
        assertEquals(2, swapMove)

        // Accent, surface, key, blankness, and total-count changes all invalidate cleanly.
        val base = painted[3]
        assertFalse(DockVisualDiff.needsRepaint(base, base))
        assertTrue(DockVisualDiff.needsRepaint(base, base, forceRepaint = true))
        assertTrue(DockVisualDiff.needsRepaint(base, base.copy(accent = ThemePack.THREEDS.accentColor)))
        assertTrue(DockVisualDiff.needsRepaint(base, base.copy(surfaceFill = ThemePack.cardFill(ThemePack.OLED))))
        assertTrue(DockVisualDiff.needsRepaint(base, base.copy(slotKey = "rom:nes/mario.nes")))
        assertTrue(DockVisualDiff.needsRepaint(base, base.copy(isBlank = true)))
        assertTrue(DockVisualDiff.needsRepaint(base, base.copy(visibleTotal = 7)))
    }

    @Test
    fun `focusing blank dock slot repaints at most 2 slots and transitions blank plus highlight cleanly`() {
        val total = 5
        val painted = Array(total) { i ->
            slotState(
                index = i,
                focused = 3,
                slotKey = if (i < 4) "com.app.$i" else null,
                visibleTotal = total,
            )
        }
        // Step right from slot 3 (filled) -> slot 4 (blank '+'): strictly 2 slots repaint.
        var mutations = 0
        for (i in 0 until total) {
            val next = slotState(
                index = i,
                focused = 4,
                slotKey = if (i < 4) "com.app.$i" else null,
                visibleTotal = total,
            )
            if (DockVisualDiff.needsRepaint(painted[i], next)) {
                painted[i] = next
                mutations++
            }
        }
        assertEquals(2, mutations)
        assertTrue(painted[4].isBlank)
        assertEquals(MoveChoreography.SlotRole.FOCUSED, painted[4].role)
        assertTrue(
            DockBarMetrics.blankPlusColorForRole(painted[4].role) !=
                DockBarMetrics.blankPlusColorForRole(MoveChoreography.SlotRole.NORMAL),
        )
    }
}
