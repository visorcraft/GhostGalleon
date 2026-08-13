package com.visorcraft.ghostgalleon.input

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchPointerTest {

    @Test
    fun `corners and clamp map onto display rect`() {
        val left = 10
        val top = 20
        val width = 101
        val height = 201
        assertEquals(
            10 to 20,
            LaunchPointer.mapNormToDisplay(0f, 0f, left, top, width, height),
        )
        assertEquals(
            110 to 220,
            LaunchPointer.mapNormToDisplay(1f, 1f, left, top, width, height),
        )
        assertEquals(
            60 to 120,
            LaunchPointer.mapNormToDisplay(0.5f, 0.5f, left, top, width, height),
        )
        assertEquals(
            10 to 20,
            LaunchPointer.mapNormToDisplay(-1f, -0.5f, left, top, width, height),
        )
        assertEquals(
            110 to 220,
            LaunchPointer.mapNormToDisplay(2f, 1.5f, left, top, width, height),
        )
    }

    @Test
    fun `zero size rect stays at origin`() {
        assertEquals(
            0 to 0,
            LaunchPointer.mapNormToDisplay(0.5f, 0.5f, 0, 0, 0, 0),
        )
    }
}
