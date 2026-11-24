package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.rom.RomEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerHighlightTest {

    private fun app(pkg: String) = AppEntry(pkg, pkg, isGame = false)

    private fun rom(id: String) = RomEntry(
        id = id,
        name = id,
        platformId = "snes",
        uri = "content://$id",
        path = null,
    )

    private val mixed: List<PickerItem> = listOf(
        PickerItem.Header(PickerItem.Header.Section.APPS), // 0
        PickerItem.App(app("a1")), // 1
        PickerItem.App(app("a2")), // 2
        PickerItem.Header(PickerItem.Header.Section.ROMS), // 3
        PickerItem.Rom(rom("r1")), // 4
        PickerItem.Rom(rom("r2")), // 5
        PickerItem.Rom(rom("r3")), // 6
    )

    @Test
    fun `move skips section headers and emits only previous and next positions`() {
        // Step down across ROMs header (2 -> 4): only 2 positions notified, never all N.
        val down = AppPickerHighlight.move(mixed, current = 2, delta = 1)
        assertEquals(2, down.previous)
        assertEquals(4, down.current)
        assertEquals(listOf(2, 4), down.changedPositions)

        // Step up across ROMs header (4 -> 2).
        val up = AppPickerHighlight.move(mixed, current = 4, delta = -1)
        assertEquals(4, up.previous)
        assertEquals(2, up.current)
        assertEquals(listOf(4, 2), up.changedPositions)
    }

    @Test
    fun `page jump near top stops on first selectable item instead of refusing to move`() {
        // Jumping -5 from index 4 hits header at 0 and lands on last valid selectable row (1).
        val jumpTop = AppPickerHighlight.move(mixed, current = 4, delta = -5)
        assertEquals(4, jumpTop.previous)
        assertEquals(1, jumpTop.current)
        assertEquals(listOf(4, 1), jumpTop.changedPositions)

        // Jumping +5 from index 2 clamps cleanly on the final ROM row (6).
        val jumpBottom = AppPickerHighlight.move(mixed, current = 2, delta = 5)
        assertEquals(2, jumpBottom.previous)
        assertEquals(6, jumpBottom.current)
        assertEquals(listOf(2, 6), jumpBottom.changedPositions)

        // Already at the boundary emits no redundant notifications.
        val noop = AppPickerHighlight.move(mixed, current = 1, delta = -5)
        assertEquals(1, noop.current)
        assertTrue(noop.changedPositions.isEmpty())
    }

    @Test
    fun `large 6500-item list emits at most two changed positions on move`() {
        val large = buildList<PickerItem>(6_501) {
            add(PickerItem.Header(PickerItem.Header.Section.ROMS))
            repeat(6_500) { i -> add(PickerItem.Rom(rom("snes:rom_$i"))) }
        }
        assertEquals(6_500, AppPickerHighlight.selectableCount(large))
        val move = AppPickerHighlight.move(large, current = 3_000, delta = 5)
        assertEquals(3_005, move.current)
        assertEquals(listOf(3_000, 3_005), move.changedPositions)
    }

    @Test
    fun `afterFilter preserves matching key or resets to first selectable row`() {
        assertEquals(4, AppPickerHighlight.afterFilter(mixed, previousKey = "r1"))
        assertEquals(1, AppPickerHighlight.afterFilter(mixed, previousKey = "missing"))
        assertEquals(0, AppPickerHighlight.afterFilter(emptyList(), previousKey = "r1"))
    }

    @Test
    fun `positionsToRefreshAfterFilter stays bounded and deduplicated`() {
        assertEquals(emptyList<Int>(), AppPickerHighlight.positionsToRefreshAfterFilter(1, 1, newSize = 0))
        assertEquals(listOf(1), AppPickerHighlight.positionsToRefreshAfterFilter(1, 1, newSize = 5))
        assertEquals(listOf(3, 1), AppPickerHighlight.positionsToRefreshAfterFilter(3, 1, newSize = 5))
        assertEquals(listOf(1), AppPickerHighlight.positionsToRefreshAfterFilter(99, 1, newSize = 5))
    }
}
