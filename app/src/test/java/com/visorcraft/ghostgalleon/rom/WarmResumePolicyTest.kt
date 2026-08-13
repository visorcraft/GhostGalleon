package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmResumePolicyTest {

    @Test
    fun `probe only when idle RA continue is due`() {
        assertTrue(
            WarmResumePolicy.mayProbe(true, false, "rom:a", true, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, true, "rom:a", true, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(false, false, "rom:a", true, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, false, null, true, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, false, "rom:a", false, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, false, "rom:a", true, false, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, false, "rom:a", true, true, 10_000L, 20_000L),
        )
    }

    @Test
    fun `autoload only for continue with a slot`() {
        assertTrue(
            WarmResumePolicy.mayAutoload(true, LaunchReason.CONTINUE, true, 10, false),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(true, LaunchReason.SWITCHER, true, 10, false),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(true, LaunchReason.SLOT, true, 10, false),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(false, LaunchReason.CONTINUE, true, 10, false),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(true, LaunchReason.CONTINUE, true, 10, true),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(true, LaunchReason.CONTINUE, true, null, false),
        )
        assertEquals(10, WarmResumePolicy.loadSlot(10, 11, 1))
        assertEquals(11, WarmResumePolicy.loadSlot(null, 11, 1))
        assertEquals(1, WarmResumePolicy.loadSlot(null, null, 1))
        assertNull(WarmResumePolicy.loadSlot(null, null, null))
    }
}
