package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action

/**
 * Pure, host-tested 2D grid wrap/clamp navigation math for modal grids
 * (`FolderPanel`, `QuickPanel`, etc.).
 *
 * Guarantees safe in-bounds indices for every item count (`0`, `1`, `2`,
 * partial last rows, arbitrary column counts) — avoiding Kotlin `%` negative
 * remainder crashes when `count < columns`.
 */
object GridWrapNav {

    /** Clamp [index] into `0 until count` (or `0` when [count] `<= 0`). */
    fun clampIndex(index: Int, count: Int): Int {
        if (count <= 0) return 0
        return index.coerceIn(0, count - 1)
    }

    /** Total rows required to render [count] items in [columns] columns (`0` if empty). */
    fun rowCount(count: Int, columns: Int): Int {
        if (count <= 0) return 0
        val cols = columns.coerceAtLeast(1)
        return (count + cols - 1) / cols
    }

    /** Zero-based row index of [index] in a [columns]-column grid (clamped >= 0). */
    fun rowOf(index: Int, columns: Int): Int =
        index.coerceAtLeast(0) / columns.coerceAtLeast(1)

    /** Zero-based column index of [index] in a [columns]-column grid (clamped >= 0). */
    fun colOf(index: Int, columns: Int): Int =
        index.coerceAtLeast(0) % columns.coerceAtLeast(1)

    /** Number of invisible filler slots needed to align a partial last row (`0` when full/empty). */
    fun trailingSpacers(count: Int, columns: Int): Int {
        if (count <= 0) return 0
        val cols = columns.coerceAtLeast(1)
        val rem = count % cols
        return if (rem == 0) 0 else cols - rem
    }

    /**
     * Move [index] by horizontal delta [dx] and vertical delta [dy] in a grid
     * of [count] items with [columns] columns.
     *
     * - Returns `0` when [count] `<= 0`.
     * - Horizontal moves (`dx != 0`) wrap linearly across `0 until count`.
     * - Vertical moves (`dy != 0`) wrap across rows preserving column, clamping
     *   into the last item of a partial final row when that column is absent.
     */
    fun move(index: Int, count: Int, columns: Int, dx: Int, dy: Int): Int {
        if (count <= 0) return 0
        val cols = columns.coerceAtLeast(1)
        var cur = index.coerceIn(0, count - 1)
        if (dy != 0) {
            val totalRows = (count + cols - 1) / cols
            if (totalRows > 1) {
                val row = cur / cols
                val col = cur % cols
                val nextRow = floorMod(row + dy, totalRows)
                cur = (nextRow * cols + col).coerceAtMost(count - 1)
            }
        }
        if (dx != 0) {
            cur = floorMod(cur + dx, count)
        }
        return cur
    }

    /** Convenience mapper from gamepad [Action] to [move]. */
    fun moveAction(index: Int, count: Int, columns: Int, action: Action): Int =
        when (action) {
            Action.NAV_LEFT -> move(index, count, columns, dx = -1, dy = 0)
            Action.NAV_RIGHT -> move(index, count, columns, dx = 1, dy = 0)
            Action.NAV_UP -> move(index, count, columns, dx = 0, dy = -1)
            Action.NAV_DOWN -> move(index, count, columns, dx = 0, dy = 1)
            else -> clampIndex(index, count)
        }

    /**
     * Jump by [deltaPages] pages of [pageSize] items, clamped to `0 until count`
     * (used by L1/R1 page-jump in modal lists/grids).
     */
    fun pageMove(index: Int, count: Int, pageSize: Int, deltaPages: Int): Int {
        if (count <= 0) return 0
        val cur = index.coerceIn(0, count - 1)
        val step = pageSize.coerceAtLeast(1)
        return (cur + deltaPages * step).coerceIn(0, count - 1)
    }

    private fun floorMod(value: Int, modulus: Int): Int =
        ((value % modulus) + modulus) % modulus
}
