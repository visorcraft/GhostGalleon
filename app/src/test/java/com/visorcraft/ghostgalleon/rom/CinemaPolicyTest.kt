package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaPolicyTest {

    @Test
    fun `nextSlot walks the reserved band only`() {
        assertEquals(9, CinemaPolicy.nextSlot(null))
        assertEquals(10, CinemaPolicy.nextSlot(9))
        assertEquals(11, CinemaPolicy.nextSlot(10))
        assertEquals(12, CinemaPolicy.nextSlot(11))
        assertEquals(9, CinemaPolicy.nextSlot(12))
        assertEquals(9, CinemaPolicy.nextSlot(3))
        assertTrue(CinemaPolicy.inBand(9))
        assertFalse(CinemaPolicy.inBand(8))
    }

    @Test
    fun `capture stays off when the host or slots are dead`() {
        assertTrue(
            CinemaPolicy.shouldCapture(true, true, true, true, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(false, true, true, true, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(true, false, true, true, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(true, true, false, true, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(true, true, true, false, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(true, true, true, true, 10_000L, 20_000L, 60_000L),
        )
        assertEquals(15_000L, CinemaPolicy.clampInterval(100L))
        assertEquals(300_000L, CinemaPolicy.clampInterval(999_999L))
    }
}
