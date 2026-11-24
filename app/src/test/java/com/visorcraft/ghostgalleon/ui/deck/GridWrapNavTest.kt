package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridWrapNavTest {

    @Test
    fun `empty and single-item grids never go out of bounds`() {
        assertEquals(0, GridWrapNav.move(0, count = 0, columns = 3, dx = -1, dy = 0))
        assertEquals(0, GridWrapNav.move(0, count = 0, columns = 3, dx = 0, dy = -1))
        assertEquals(0, GridWrapNav.move(0, count = 1, columns = 3, dx = 1, dy = 0))
        assertEquals(0, GridWrapNav.move(0, count = 1, columns = 3, dx = -1, dy = 0))
        assertEquals(0, GridWrapNav.move(0, count = 1, columns = 3, dx = 0, dy = 1))
        assertEquals(0, GridWrapNav.move(0, count = 1, columns = 3, dx = 0, dy = -1))
    }

    @Test
    fun `two-item folder grid does not crash on NAV_UP or NAV_DOWN`() {
        // Exact FolderPanel crash regression: count=2, cols=3, index=0, NAV_UP
        assertEquals(0, GridWrapNav.move(0, count = 2, columns = 3, dx = 0, dy = -1))
        assertEquals(1, GridWrapNav.move(1, count = 2, columns = 3, dx = 0, dy = -1))
        assertEquals(0, GridWrapNav.move(0, count = 2, columns = 3, dx = 0, dy = 1))
        assertEquals(1, GridWrapNav.move(1, count = 2, columns = 3, dx = 0, dy = 1))
        // Horizontal wraps cleanly across the 2 items.
        assertEquals(1, GridWrapNav.move(0, count = 2, columns = 3, dx = -1, dy = 0))
        assertEquals(0, GridWrapNav.move(1, count = 2, columns = 3, dx = 1, dy = 0))
    }

    @Test
    fun `full 3x3 grid wraps horizontally and vertically`() {
        assertEquals(8, GridWrapNav.move(0, count = 9, columns = 3, dx = -1, dy = 0))
        assertEquals(0, GridWrapNav.move(8, count = 9, columns = 3, dx = 1, dy = 0))
        assertEquals(6, GridWrapNav.move(0, count = 9, columns = 3, dx = 0, dy = -1))
        assertEquals(3, GridWrapNav.move(0, count = 9, columns = 3, dx = 0, dy = 1))
        assertEquals(2, GridWrapNav.move(8, count = 9, columns = 3, dx = 0, dy = 1))
        assertEquals(5, GridWrapNav.move(8, count = 9, columns = 3, dx = 0, dy = -1))
    }

    @Test
    fun `partial last row clamps column into last valid item and wraps back`() {
        // 4 columns, 5 items: row 0 = [0, 1, 2, 3], row 1 = [4]
        assertEquals(4, GridWrapNav.move(0, count = 5, columns = 4, dx = 0, dy = 1))
        assertEquals(4, GridWrapNav.move(2, count = 5, columns = 4, dx = 0, dy = 1))
        assertEquals(4, GridWrapNav.move(3, count = 5, columns = 4, dx = 0, dy = -1))
        assertEquals(0, GridWrapNav.move(4, count = 5, columns = 4, dx = 0, dy = 1))
        assertEquals(0, GridWrapNav.move(4, count = 5, columns = 4, dx = 0, dy = -1))

        // 3 columns, 7 items: row 0 = [0,1,2], row 1 = [3,4,5], row 2 = [6]
        assertEquals(5, GridWrapNav.move(2, count = 7, columns = 3, dx = 0, dy = 1))
        assertEquals(6, GridWrapNav.move(5, count = 7, columns = 3, dx = 0, dy = 1))
        assertEquals(6, GridWrapNav.move(2, count = 7, columns = 3, dx = 0, dy = -1))
        assertEquals(3, GridWrapNav.move(6, count = 7, columns = 3, dx = 0, dy = -1))
        assertEquals(0, GridWrapNav.move(6, count = 7, columns = 3, dx = 0, dy = 1))
    }

    @Test
    fun `exhaustive bounds check across counts 1 to 15 and columns 1 to 5`() {
        for (count in 1..15) {
            for (cols in 1..5) {
                for (i in 0 until count) {
                    for (action in listOf(
                        Action.NAV_LEFT,
                        Action.NAV_RIGHT,
                        Action.NAV_UP,
                        Action.NAV_DOWN,
                        Action.CONFIRM,
                    )) {
                        val next = GridWrapNav.moveAction(i, count, cols, action)
                        assertTrue(
                            "out of bounds: next=$next for i=$i count=$count cols=$cols action=$action",
                            next in 0 until count,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `invalid indices and zero columns are safely clamped`() {
        assertEquals(0, GridWrapNav.move(-10, count = 4, columns = 0, dx = 0, dy = 0))
        assertEquals(3, GridWrapNav.move(99, count = 4, columns = 2, dx = 0, dy = 0))
        assertEquals(0, GridWrapNav.clampIndex(5, count = 0))
    }

    @Test
    fun `pageMove jumps by page size and clamps to list bounds`() {
        assertEquals(0, GridWrapNav.pageMove(0, count = 0, pageSize = 5, deltaPages = 1))
        assertEquals(5, GridWrapNav.pageMove(0, count = 12, pageSize = 5, deltaPages = 1))
        assertEquals(11, GridWrapNav.pageMove(9, count = 12, pageSize = 5, deltaPages = 1))
        assertEquals(0, GridWrapNav.pageMove(2, count = 12, pageSize = 5, deltaPages = -1))
    }

    @Test
    fun `rowCount, rowOf, colOf, and trailingSpacers compute safe grid geometry`() {
        assertEquals(0, GridWrapNav.rowCount(count = 0, columns = 4))
        assertEquals(2, GridWrapNav.rowCount(count = 8, columns = 4))
        assertEquals(3, GridWrapNav.rowCount(count = 9, columns = 4))
        assertEquals(4, GridWrapNav.rowCount(count = 4, columns = 0))

        assertEquals(0, GridWrapNav.rowOf(index = 2, columns = 4))
        assertEquals(2, GridWrapNav.colOf(index = 2, columns = 4))
        assertEquals(2, GridWrapNav.rowOf(index = 9, columns = 4))
        assertEquals(1, GridWrapNav.colOf(index = 9, columns = 4))
        assertEquals(0, GridWrapNav.rowOf(index = -3, columns = 4))
        assertEquals(0, GridWrapNav.colOf(index = -3, columns = 4))

        assertEquals(0, GridWrapNav.trailingSpacers(count = 0, columns = 4))
        assertEquals(0, GridWrapNav.trailingSpacers(count = 8, columns = 4))
        assertEquals(3, GridWrapNav.trailingSpacers(count = 9, columns = 4))
        assertEquals(1, GridWrapNav.trailingSpacers(count = 5, columns = 3))
    }
}
