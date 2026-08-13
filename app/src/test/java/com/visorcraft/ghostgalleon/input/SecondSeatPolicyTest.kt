package com.visorcraft.ghostgalleon.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondSeatPolicyTest {

    @Test
    fun `seat only for dual KEEP RA with assist`() {
        assertTrue(
            SecondSeatPolicy.allowed(true, true, false, true, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(false, true, false, true, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, false, false, true, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, true, true, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, false, false, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, false, true, false, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, false, true, true, false, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, false, true, true, true, true),
        )
    }

    @Test
    fun `empty stored anchors fall back and stay normalized`() {
        val d = SecondSeatPolicy.anchorsOrDefault(emptyList())
        assertTrue(d.any { it.id == "a" })
        val custom = listOf(SeatAnchor("a", 1.5f, -0.2f))
        val one = SecondSeatPolicy.anchorsOrDefault(custom)
        assertEquals(1, one.size)
        val (x, y) = SecondSeatPolicy.point(SeatAnchor("a", 0.5f, 0.25f), 200, 100)
        assertEquals(100f, x)
        assertEquals(25f, y)
    }
}
