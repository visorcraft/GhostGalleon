package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPanelNavTest {

    @Test
    fun `folder navigation across counts 0 1 2 3 4 5 7 never goes out of bounds without mirror`() {
        val counts = listOf(0, 1, 2, 3, 4, 5, 7)
        val actions = listOf(
            Action.NAV_LEFT,
            Action.NAV_RIGHT,
            Action.NAV_UP,
            Action.NAV_DOWN,
            Action.PAGE_PREV,
            Action.PAGE_NEXT,
        )
        for (count in counts) {
            val indices = if (count == 0) listOf(0) else (0 until count).toList()
            for (start in indices) {
                for (action in actions) {
                    val next = FolderPanelNav.step(
                        state = FolderNavState(selectedCell = start, mirrorFocused = false),
                        memberCount = count,
                        hasMirror = false,
                        action = action,
                    )
                    assertFalse(next.mirrorFocused)
                    if (count == 0) {
                        assertEquals(0, next.selectedCell)
                    } else {
                        assertTrue(
                            "out of bounds: next=$next for count=$count start=$start action=$action",
                            next.selectedCell in 0 until count,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `two-item folder crash regression stays valid in all four directions`() {
        // Previously `(0 + 2 - 3) % 2` evaluated to `-1` and crashed on CONFIRM.
        assertEquals(
            FolderNavState(0, false),
            FolderPanelNav.step(FolderNavState(0, false), memberCount = 2, hasMirror = false, action = Action.NAV_UP),
        )
        assertEquals(
            FolderNavState(1, false),
            FolderPanelNav.step(FolderNavState(1, false), memberCount = 2, hasMirror = false, action = Action.NAV_UP),
        )
        assertEquals(
            FolderNavState(1, false),
            FolderPanelNav.step(FolderNavState(0, false), memberCount = 2, hasMirror = false, action = Action.NAV_LEFT),
        )
        assertEquals(
            FolderNavState(0, false),
            FolderPanelNav.step(FolderNavState(1, false), memberCount = 2, hasMirror = false, action = Action.NAV_RIGHT),
        )
    }

    @Test
    fun `mirror footer is reachable from last or first row and returns preserving column`() {
        // 5 items in 3 columns: row 0 = [0, 1, 2], row 1 = [3, 4]
        // From row 0 col 1 (index 1) -> DOWN goes to row 1 col 1 (index 4).
        val row1 = FolderPanelNav.step(
            FolderNavState(1, false), memberCount = 5, hasMirror = true, action = Action.NAV_DOWN,
        )
        assertEquals(FolderNavState(4, false), row1)

        // From row 1 (last row, index 4) -> DOWN focuses Mirror footer.
        val mirror = FolderPanelNav.step(
            row1, memberCount = 5, hasMirror = true, action = Action.NAV_DOWN,
        )
        assertEquals(FolderNavState(4, true), mirror)

        // LEFT/RIGHT keep focus on Mirror.
        assertEquals(
            mirror,
            FolderPanelNav.step(mirror, memberCount = 5, hasMirror = true, action = Action.NAV_LEFT),
        )
        assertEquals(
            mirror,
            FolderPanelNav.step(mirror, memberCount = 5, hasMirror = true, action = Action.NAV_RIGHT),
        )

        // UP from Mirror returns to last row preserving column (col 1 -> index 4).
        assertEquals(
            FolderNavState(4, false),
            FolderPanelNav.step(mirror, memberCount = 5, hasMirror = true, action = Action.NAV_UP),
        )

        // DOWN from Mirror wraps back to row 0 preserving column (col 1 -> index 1).
        assertEquals(
            FolderNavState(1, false),
            FolderPanelNav.step(mirror, memberCount = 5, hasMirror = true, action = Action.NAV_DOWN),
        )

        // UP from row 0 (index 2) wraps cleanly to Mirror, and UP from Mirror clamps col 2 -> index 4.
        val upToMirror = FolderPanelNav.step(
            FolderNavState(2, false), memberCount = 5, hasMirror = true, action = Action.NAV_UP,
        )
        assertEquals(FolderNavState(2, true), upToMirror)
        assertEquals(
            FolderNavState(4, false),
            FolderPanelNav.step(upToMirror, memberCount = 5, hasMirror = true, action = Action.NAV_UP),
        )
    }

    @Test
    fun `seven-item folder with mirror traverses all three rows and footer cleanly`() {
        // Row 0 = [0, 1, 2], row 1 = [3, 4, 5], row 2 = [6]
        val s1 = FolderPanelNav.step(FolderNavState(2, false), 7, hasMirror = true, action = Action.NAV_DOWN)
        assertEquals(FolderNavState(5, false), s1)
        val s2 = FolderPanelNav.step(s1, 7, hasMirror = true, action = Action.NAV_DOWN)
        assertEquals(FolderNavState(6, false), s2)
        val s3 = FolderPanelNav.step(s2, 7, hasMirror = true, action = Action.NAV_DOWN)
        assertEquals(FolderNavState(6, true), s3)
        val s4 = FolderPanelNav.step(s3, 7, hasMirror = true, action = Action.NAV_UP)
        assertEquals(FolderNavState(6, false), s4)
        val s5 = FolderPanelNav.step(s4, 7, hasMirror = true, action = Action.NAV_UP)
        assertEquals(FolderNavState(3, false), s5)
    }

    @Test
    fun `afterRemoval clamps index and clears mirror focus`() {
        assertEquals(FolderNavState(0, false), FolderPanelNav.afterRemoval(FolderNavState(0, false), 0))
        assertEquals(FolderNavState(1, false), FolderPanelNav.afterRemoval(FolderNavState(2, false), 2))
        assertEquals(FolderNavState(0, false), FolderPanelNav.afterRemoval(FolderNavState(1, true), 1))
    }

    @Test
    fun `dirtyUpdate touches at most two cells per NAV tick and updates mirror only on flip`() {
        val initial = FolderPanelNav.dirtyUpdate(
            previous = null,
            next = FolderNavState(0, false),
            cellCount = 9,
            hasMirror = true,
        )
        assertEquals((0 until 9).toList(), initial.cellIndices)
        assertTrue(initial.updateMirror)

        val cellMove = FolderPanelNav.dirtyUpdate(
            previous = FolderNavState(0, false),
            next = FolderNavState(3, false),
            cellCount = 9,
            hasMirror = true,
        )
        assertEquals(listOf(0, 3), cellMove.cellIndices)
        assertFalse(cellMove.updateMirror)

        val toMirror = FolderPanelNav.dirtyUpdate(
            previous = FolderNavState(6, false),
            next = FolderNavState(6, true),
            cellCount = 9,
            hasMirror = true,
        )
        assertEquals(listOf(6), toMirror.cellIndices)
        assertTrue(toMirror.updateMirror)

        val stayMirror = FolderPanelNav.dirtyUpdate(
            previous = FolderNavState(6, true),
            next = FolderNavState(6, true),
            cellCount = 9,
            hasMirror = true,
        )
        assertEquals(emptyList<Int>(), stayMirror.cellIndices)
        assertFalse(stayMirror.updateMirror)

        val fromMirror = FolderPanelNav.dirtyUpdate(
            previous = FolderNavState(6, true),
            next = FolderNavState(0, false),
            cellCount = 9,
            hasMirror = true,
        )
        assertEquals(listOf(0), fromMirror.cellIndices)
        assertTrue(fromMirror.updateMirror)
    }
}
