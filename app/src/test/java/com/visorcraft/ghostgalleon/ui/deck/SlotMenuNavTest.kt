package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotMenuNavTest {

    @Test
    fun `actionRowIndices skips all section headers cleanly`() {
        val rows = listOf(
            SlotMenu.Row.Header(R.string.menu_section_arrange),
            SlotMenu.Row.Item(SlotMenu.Choice.MOVE),
            SlotMenu.Row.Item(SlotMenu.Choice.REMOVE_FROM_GRID, destructive = true),
            SlotMenu.Row.Header(R.string.menu_section_more),
            SlotMenu.Row.Item(SlotMenu.Choice.CANCEL),
        )
        assertEquals(listOf(1, 2, 4), SlotMenuNav.actionRowIndices(rows))
    }

    @Test
    fun `move wraps on NAV_UP and NAV_DOWN and clamps on L1 R1 page jumps`() {
        assertEquals(0, SlotMenuNav.move(0, actionCount = 0, action = Action.NAV_DOWN))
        assertEquals(9, SlotMenuNav.move(0, actionCount = 10, action = Action.NAV_UP))
        assertEquals(0, SlotMenuNav.move(9, actionCount = 10, action = Action.NAV_DOWN))
        assertEquals(5, SlotMenuNav.move(0, actionCount = 10, action = Action.PAGE_NEXT))
        assertEquals(9, SlotMenuNav.move(7, actionCount = 10, action = Action.PAGE_NEXT))
        assertEquals(0, SlotMenuNav.move(2, actionCount = 10, action = Action.PAGE_PREV))
        assertEquals(4, SlotMenuNav.move(9, actionCount = 10, action = Action.PAGE_PREV))
    }

    @Test
    fun `isDestructive flags remove, remove from grid, hide, and explicit overrides`() {
        assertTrue(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.REMOVE)))
        assertTrue(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.REMOVE_FROM_GRID)))
        assertTrue(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.HIDE)))
        assertTrue(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.MOVE, destructive = true)))
        assertFalse(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.MOVE)))
        assertFalse(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.CANCEL)))
    }
}
