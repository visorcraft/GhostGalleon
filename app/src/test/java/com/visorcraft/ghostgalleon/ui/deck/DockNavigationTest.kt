package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockNavigationTest {

    // 15 grid slots (3 rows of 5), 5 dock slots.
    private val nav = DockNavigation(dockCount = 5, gridCount = 15, columns = 5)

    @Test
    fun `isLastRow only matches the final grid row`() {
        assertFalse(nav.isLastRow(0))
        assertFalse(nav.isLastRow(9))
        assertTrue(nav.isLastRow(10))
        assertTrue(nav.isLastRow(14))
    }

    @Test
    fun `isLastRow tolerates a single-row grid`() {
        val single = DockNavigation(dockCount = 5, gridCount = 5, columns = 5)
        assertTrue(single.isLastRow(0))
        assertTrue(single.isLastRow(4))
    }

    @Test
    fun `enterFromGrid keeps the column`() {
        assertEquals(0, nav.enterFromGrid(10))
        assertEquals(3, nav.enterFromGrid(13))
        assertEquals(4, nav.enterFromGrid(14))
    }

    @Test
    fun `enterFromGrid clamps wide columns into the dock`() {
        val narrow = DockNavigation(dockCount = 3, gridCount = 21, columns = 7)
        assertEquals(2, narrow.enterFromGrid(20)) // column 6 clamps to slot 2
    }

    @Test
    fun `exitToGrid returns the same column on the last row`() {
        assertEquals(10, nav.exitToGrid(0))
        assertEquals(13, nav.exitToGrid(3))
        assertEquals(14, nav.exitToGrid(4))
    }

    @Test
    fun `exitToGrid clamps a wide dock to the grid's last slot`() {
        val wide = DockNavigation(dockCount = 8, gridCount = 15, columns = 5)
        assertEquals(14, wide.exitToGrid(7))
    }

    @Test
    fun `exitToGrid of an empty grid is slot 0`() {
        val empty = DockNavigation(dockCount = 5, gridCount = 0, columns = 5)
        assertEquals(0, empty.exitToGrid(2))
    }

    @Test
    fun `move walks left and right and clamps at both ends`() {
        assertEquals(2, nav.move(3, Action.NAV_LEFT))
        assertEquals(4, nav.move(3, Action.NAV_RIGHT))
        assertEquals(0, nav.move(0, Action.NAV_LEFT))
        assertEquals(4, nav.move(4, Action.NAV_RIGHT))
    }

    @Test
    fun `move ignores non-horizontal actions`() {
        assertEquals(2, nav.move(2, Action.NAV_UP))
        assertEquals(2, nav.move(2, Action.NAV_DOWN))
        assertEquals(2, nav.move(2, Action.CONFIRM))
    }

    @Test
    fun `ragged last row and zero-count edges behave cleanly`() {
        // 12 items in 5 columns -> row 0 [0..4], row 1 [5..9], row 2 [10, 11].
        val ragged = DockNavigation(dockCount = 5, gridCount = 12, columns = 5)
        assertFalse(ragged.isLastRow(7))
        assertFalse(ragged.isLastRow(9))
        assertTrue(ragged.isLastRow(10))
        assertTrue(ragged.isLastRow(11))
        // Exiting dock col 0 returns slot 10 (first item of ragged last row), col 4 clamps to 11.
        assertEquals(10, ragged.exitToGrid(0))
        assertEquals(11, ragged.exitToGrid(1))
        assertEquals(11, ragged.exitToGrid(4))

        // Zero dock count / negative indices never throw or go negative.
        val zeroDock = DockNavigation(dockCount = 0, gridCount = 10, columns = 0)
        assertEquals(0, zeroDock.enterFromGrid(-3))
        assertEquals(0, zeroDock.move(-1, Action.NAV_LEFT))
        assertEquals(9, zeroDock.exitToGrid(-2))
    }
}
