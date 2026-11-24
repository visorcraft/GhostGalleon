package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action

// Pure dock focus math, shared by the grid and game decks. The dock is a
// fixed row of slots below the deck content: NAV DOWN from the grid's last
// row (or from the carousel) moves focus into the dock, LEFT/RIGHT walk
// the slots, and NAV UP returns to the grid (same column, clamped to the
// last row) or the carousel. Host-tested in DockNavigationTest.
class DockNavigation(
    dockCount: Int,
    gridCount: Int,
    columns: Int,
) {
    private val safeDockCount = dockCount.coerceAtLeast(0)
    private val safeGridCount = gridCount.coerceAtLeast(0)
    private val safeColumns = columns.coerceAtLeast(1)

    private val lastRowStart: Int
        get() = if (safeGridCount <= 0) 0 else ((safeGridCount - 1) / safeColumns) * safeColumns

    // True when [gridIndex] sits on the grid's last row — the only row
    // NAV DOWN leaves the grid from (handles both full and ragged last rows).
    fun isLastRow(gridIndex: Int): Boolean {
        if (safeGridCount <= 0) return true
        val clamped = gridIndex.coerceIn(0, safeGridCount - 1)
        return clamped >= lastRowStart
    }

    // Dock slot to focus when pressing DOWN from [gridIndex]: the same
    // column, clamped into the dock row.
    fun enterFromGrid(gridIndex: Int): Int {
        if (safeDockCount <= 0) return 0
        val safeIdx = gridIndex.coerceAtLeast(0)
        return (safeIdx % safeColumns).coerceIn(0, safeDockCount - 1)
    }

    // Grid slot to focus when pressing UP from [dockIndex]: the same
    // column on the last row, clamped to the grid (the dock can be wider
    // or narrower than the grid's column count).
    fun exitToGrid(dockIndex: Int): Int {
        if (safeGridCount <= 0) return 0
        val col = dockIndex.coerceAtLeast(0)
        return (lastRowStart + col).coerceIn(0, safeGridCount - 1)
    }

    // LEFT/RIGHT walk the dock row, clamped at both ends; every other
    // action stays put.
    fun move(dockIndex: Int, action: Action): Int {
        if (safeDockCount <= 0) return 0
        val current = dockIndex.coerceIn(0, safeDockCount - 1)
        val target = when (action) {
            Action.NAV_LEFT -> current - 1
            Action.NAV_RIGHT -> current + 1
            else -> current
        }
        return target.coerceIn(0, safeDockCount - 1)
    }
}
