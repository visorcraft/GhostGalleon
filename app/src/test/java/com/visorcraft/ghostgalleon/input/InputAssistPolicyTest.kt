package com.visorcraft.ghostgalleon.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputAssistPolicyTest {

    @Test
    fun `filter only when connected GAME and not yield`() {
        assertTrue(InputAssistPolicy.mayFilterKeys(true, InputOwner.GAME, false))
        assertFalse(InputAssistPolicy.mayFilterKeys(false, InputOwner.GAME, false))
        assertFalse(InputAssistPolicy.mayFilterKeys(true, InputOwner.HOST, false))
        assertFalse(InputAssistPolicy.mayFilterKeys(true, InputOwner.GAME, true))
        assertFalse(InputAssistPolicy.mayFilterKeys(true, InputOwner.NONE, false))
    }

    @Test
    fun `pointer only for KEEP winlator play host`() {
        assertTrue(
            InputAssistPolicy.mayInjectPointer(true, true, false, "winlator"),
        )
        assertTrue(
            InputAssistPolicy.mayInjectPointer(true, true, false, "winlator-main"),
        )
        assertFalse(
            InputAssistPolicy.mayInjectPointer(true, true, false, "ra-snes9x"),
        )
        assertFalse(
            InputAssistPolicy.mayInjectPointer(true, true, true, "winlator"),
        )
        assertFalse(
            InputAssistPolicy.mayInjectPointer(false, true, false, "winlator"),
        )
        assertFalse(
            InputAssistPolicy.mayInjectPointer(true, false, false, "winlator"),
        )
    }
}
