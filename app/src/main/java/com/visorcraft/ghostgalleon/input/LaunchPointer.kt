package com.visorcraft.ghostgalleon.input

/**
 * Pure mapping of normalized pad coords (0..1) onto a display rectangle.
 * Used by assist pointer inject; host-testable without Android types.
 */
object LaunchPointer {
    fun mapNormToDisplay(
        normX: Float,
        normY: Float,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        val nx = normX.coerceIn(0f, 1f)
        val ny = normY.coerceIn(0f, 1f)
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val x = left + (nx * (w - 1)).toInt()
        val y = top + (ny * (h - 1)).toInt()
        return x to y
    }
}
