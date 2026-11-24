package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridVisualDiffTest {

    private fun stateFor(
        position: Int,
        selectedSlot: Int,
        movingSlot: Int? = null,
        dockFocused: Boolean = false,
        slotKey: String? = "rom:snes/game_$position.sfc",
        isBlank: Boolean = slotKey == null,
        isMissingRom: Boolean = false,
        accent: Int = ThemePack.GHOST.accentColor,
        surfaceFill: Int = ThemePack.DEFAULT_CARD_FILL,
        docked: Boolean = false,
        favorited: Boolean = false,
        slotCount: Int = 96,
    ): GridCellVisualState {
        val role = MoveChoreography.resolveRole(
            position = position,
            selectedIndex = if (position == selectedSlot) position else null,
            movingIndex = movingSlot,
            surfaceFocused = !dockFocused,
        )
        return GridCellVisualState(
            position = position,
            role = role,
            slotKey = slotKey,
            isBlank = isBlank,
            isMissingRom = isMissingRom,
            accent = accent,
            surfaceFill = surfaceFill,
            moveActive = movingSlot != null,
            moveLifted = position == movingSlot,
            docked = docked,
            favorited = favorited,
            slotCount = slotCount,
        )
    }

    @Test
    fun `normal D-pad NAV tick across 96 attached cells mutates at most 2 cells`() {
        val totalCells = 96
        val painted = arrayOfNulls<GridCellVisualState>(totalCells)

        // Initial paint binds all 96 cells (slot 0 focused).
        var initialMutations = 0
        for (pos in 0 until totalCells) {
            val next = stateFor(position = pos, selectedSlot = 0)
            if (GridVisualDiff.needsVisualRepaint(painted[pos], next)) {
                painted[pos] = next
                initialMutations++
            }
        }
        assertEquals(totalCells, initialMutations)

        // Repeated identical selection tick mutates 0 cells.
        var noopMutations = 0
        for (pos in 0 until totalCells) {
            val next = stateFor(position = pos, selectedSlot = 0)
            if (GridVisualDiff.needsVisualRepaint(painted[pos], next)) {
                painted[pos] = next
                noopMutations++
            }
        }
        assertEquals(0, noopMutations)

        // Walking across pages via D-pad mutates strictly 2 cells (prev + next) per tick.
        for (nextSel in listOf(1, 2, 16, 47, 95, 0)) {
            var mutations = 0
            for (pos in 0 until totalCells) {
                val next = stateFor(position = pos, selectedSlot = nextSel)
                if (GridVisualDiff.needsVisualRepaint(painted[pos], next)) {
                    painted[pos] = next
                    mutations++
                }
            }
            assertEquals("Expected <= 2 mutations moving to slot $nextSel", 2, mutations)
        }

        // Entering dock focus only clears the single previously focused grid cell (1 mutation).
        var dockEnterMutations = 0
        for (pos in 0 until totalCells) {
            val next = stateFor(position = pos, selectedSlot = 0, dockFocused = true)
            if (GridVisualDiff.needsVisualRepaint(painted[pos], next)) {
                painted[pos] = next
                dockEnterMutations++
            }
        }
        assertEquals(1, dockEnterMutations)
    }

    @Test
    fun `entering and exiting move mode repaints all affected cells while move steps mutate only swapped pair`() {
        val totalCells = 48
        val keys = MutableList<String?>(totalCells) { "rom:gba/t_$it.gba" }
        val painted = Array(totalCells) { pos -> stateFor(pos, selectedSlot = 5, slotKey = keys[pos], slotCount = totalCells) }

        // Entering move mode transitions slot 5 (FOCUSED -> LIFTED) and 47 siblings (NORMAL -> DROP_TARGET).
        var enterMutations = 0
        for (pos in 0 until totalCells) {
            val next = stateFor(pos, selectedSlot = 5, movingSlot = 5, slotKey = keys[pos], slotCount = totalCells)
            if (GridVisualDiff.needsVisualRepaint(painted[pos], next)) {
                painted[pos] = next
                enterMutations++
            }
        }
        assertEquals(totalCells, enterMutations)

        // Swapping slot 5 <-> 6 during move mode mutates strictly those 2 slots.
        val tmp = keys[5]
        keys[5] = keys[6]
        keys[6] = tmp
        var swapMutations = 0
        for (pos in 0 until totalCells) {
            val next = stateFor(pos, selectedSlot = 6, movingSlot = 6, slotKey = keys[pos], slotCount = totalCells)
            if (GridVisualDiff.needsVisualRepaint(painted[pos], next)) {
                painted[pos] = next
                swapMutations++
            }
        }
        assertEquals(2, swapMutations)

        // Exiting move mode repaints all 48 cells back to NORMAL / FOCUSED.
        var exitMutations = 0
        for (pos in 0 until totalCells) {
            val next = stateFor(pos, selectedSlot = 6, movingSlot = null, slotKey = keys[pos], slotCount = totalCells)
            if (GridVisualDiff.needsVisualRepaint(painted[pos], next)) {
                painted[pos] = next
                exitMutations++
            }
        }
        assertEquals(totalCells, exitMutations)
    }

    @Test
    fun `accent, surface, badge, position, and missing-rom changes invalidate cached cell visual state`() {
        val base = stateFor(position = 3, selectedSlot = 3)
        assertFalse(GridVisualDiff.needsVisualRepaint(base, base))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base, forceRepaint = true))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base.copy(accent = ThemePack.NEON.accentColor)))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base.copy(surfaceFill = ThemePack.cardFill(ThemePack.OLED))))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base.copy(favorited = true)))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base.copy(docked = true)))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base.copy(isMissingRom = true)))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base.copy(position = 19)))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base.copy(slotKey = "rom:snes/other.sfc")))
        assertTrue(GridVisualDiff.needsVisualRepaint(base, base.copy(slotCount = 112)))
    }

    @Test
    fun `classifyCellKind and canReuseSubtree enable same-kind reuse for ROM, APP, and BLANK tiles`() {
        assertEquals(
            GridCellKind.ROM,
            GridVisualDiff.classifyCellKind("rom:snes/a", hasResolvedRom = true, hasResolvedApp = false, isFolder = false, isRomKey = true),
        )
        assertEquals(
            GridCellKind.MISSING_ROM,
            GridVisualDiff.classifyCellKind("rom:snes/gone", hasResolvedRom = false, hasResolvedApp = false, isFolder = false, isRomKey = true),
        )
        assertEquals(
            GridCellKind.APP,
            GridVisualDiff.classifyCellKind("com.example.app", hasResolvedRom = false, hasResolvedApp = true, isFolder = false, isRomKey = false),
        )
        assertEquals(
            GridCellKind.FOLDER,
            GridVisualDiff.classifyCellKind("folder:f1", hasResolvedRom = false, hasResolvedApp = false, isFolder = true, isRomKey = false),
        )
        assertEquals(
            GridCellKind.BLANK,
            GridVisualDiff.classifyCellKind(null, hasResolvedRom = false, hasResolvedApp = false, isFolder = false, isRomKey = false),
        )

        // Same-kind ROM, APP, and BLANK subtrees reuse cleanly when label config matches.
        assertTrue(GridVisualDiff.canReuseSubtree(GridCellKind.ROM, GridCellKind.ROM, existingShowLabels = true, nextShowLabels = true))
        assertTrue(GridVisualDiff.canReuseSubtree(GridCellKind.ROM, GridCellKind.ROM, existingShowLabels = false, nextShowLabels = false))
        assertTrue(GridVisualDiff.canReuseSubtree(GridCellKind.APP, GridCellKind.APP, existingShowLabels = true, nextShowLabels = true))
        assertTrue(GridVisualDiff.canReuseSubtree(GridCellKind.BLANK, GridCellKind.BLANK, existingShowLabels = true, nextShowLabels = false))

        // Cross-kind transitions, label toggle mismatch, null existing, and complex folder/missing rebuild cleanly.
        assertFalse(GridVisualDiff.canReuseSubtree(null, GridCellKind.ROM, true, true))
        assertFalse(GridVisualDiff.canReuseSubtree(GridCellKind.ROM, GridCellKind.APP, true, true))
        assertFalse(GridVisualDiff.canReuseSubtree(GridCellKind.APP, GridCellKind.BLANK, true, true))
        assertFalse(GridVisualDiff.canReuseSubtree(GridCellKind.ROM, GridCellKind.ROM, existingShowLabels = true, nextShowLabels = false))
        assertFalse(GridVisualDiff.canReuseSubtree(GridCellKind.FOLDER, GridCellKind.FOLDER, true, true))
        assertFalse(GridVisualDiff.canReuseSubtree(GridCellKind.MISSING_ROM, GridCellKind.MISSING_ROM, true, true))
    }

    @Test
    fun `resolveAppCellLabel prefers trimmed custom name and falls back cleanly to stock label`() {
        val custom = mapOf(
            "com.example.retro" to "RetroArch",
            "com.example.spaced" to "  Dolphin MMJR  ",
            "com.example.blank" to "   ",
        )
        assertEquals(
            "RetroArch",
            GridVisualDiff.resolveAppCellLabel("com.example.retro", "RetroArch Plus", custom),
        )
        assertEquals(
            "Dolphin MMJR",
            GridVisualDiff.resolveAppCellLabel("com.example.spaced", "Dolphin", custom),
        )
        assertEquals(
            "Stock App",
            GridVisualDiff.resolveAppCellLabel("com.example.blank", "Stock App", custom),
        )
        assertEquals(
            "Settings",
            GridVisualDiff.resolveAppCellLabel("com.android.settings", "Settings", custom),
        )
    }
}
