package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotMenuRowsTest {

    @Test
    fun `grid app menu puts remove from grid near the top`() {
        val rows = SlotMenu.gridTileRows(
            isRom = false,
            isApp = true,
            fav = false,
            inDock = false,
            hasCustomName = false,
            hasCustomIcon = false,
            showRelated = false,
            showMarkPlayed = false,
            showClearStats = false,
        )
        val actions = rows.filterIsInstance<SlotMenu.Row.Item>().map { it.choice }
        assertEquals(SlotMenu.Choice.MOVE, actions[0])
        assertEquals(SlotMenu.Choice.REMOVE_FROM_GRID, actions[1])
        assertTrue(actions.contains(SlotMenu.Choice.PIN_TO_DOCK))
        assertTrue(actions.contains(SlotMenu.Choice.RENAME))
        assertTrue(actions.contains(SlotMenu.Choice.APP_INFO))
        assertTrue(actions.contains(SlotMenu.Choice.OPEN_IN_GAME_MODE))
        assertTrue(actions.contains(SlotMenu.Choice.SEARCH_LIBRARY))
        assertEquals(SlotMenu.Choice.CANCEL, actions.last())
    }

    @Test
    fun `grid rom menu includes hide and open with under customize`() {
        val rows = SlotMenu.gridTileRows(
            isRom = true,
            isApp = false,
            fav = true,
            inDock = true,
            hasCustomName = false,
            hasCustomIcon = false,
            showRelated = true,
            showMarkPlayed = true,
            showClearStats = false,
        )
        val actions = rows.filterIsInstance<SlotMenu.Row.Item>().map { it.choice }
        assertEquals(SlotMenu.Choice.REMOVE_FROM_GRID, actions[1])
        assertTrue(actions.contains(SlotMenu.Choice.UNPIN_FROM_DOCK))
        assertTrue(actions.contains(SlotMenu.Choice.UNFAVORITE))
        assertTrue(actions.contains(SlotMenu.Choice.RENAME))
        assertTrue(actions.contains(SlotMenu.Choice.OPEN_WITH))
        assertTrue(actions.contains(SlotMenu.Choice.DOWNLOAD_ART))
        assertTrue(actions.contains(SlotMenu.Choice.HIDE))
        assertTrue(actions.contains(SlotMenu.Choice.BROWSE_RELATED))
        val remove = rows.filterIsInstance<SlotMenu.Row.Item>()
            .first { it.choice == SlotMenu.Choice.REMOVE_FROM_GRID }
        assertTrue(remove.destructive)
    }
}
