package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitPolicyTest {
    @Test
    fun `only keep winlator play host`() {
        assertTrue(CockpitPolicy.cockpitAllowed(true, "winlator", true))
        assertTrue(CockpitPolicy.cockpitAllowed(true, "winlator-main", true))
        assertFalse(CockpitPolicy.cockpitAllowed(true, "winlator", false))
        assertFalse(CockpitPolicy.cockpitAllowed(false, "winlator", true))
        assertFalse(CockpitPolicy.cockpitAllowed(true, "ra-snes9x", true))
        assertFalse(CockpitPolicy.cockpitAllowed(true, "melondualds", true))
    }
}
