package com.visorcraft.ghostgalleon.display

import org.junit.Assert.assertEquals
import org.junit.Test

class PosturePolicyTest {

    @Test
    fun `hinge buckets`() {
        assertEquals(DevicePosture.UNKNOWN, PosturePolicy.fromSensors(null, null))
        assertEquals(DevicePosture.CLOSED, PosturePolicy.fromSensors(5f, null))
        assertEquals(DevicePosture.TABLETOP, PosturePolicy.fromSensors(90f, null))
        assertEquals(DevicePosture.BOOK, PosturePolicy.fromSensors(155f, null))
        assertEquals(DevicePosture.FLAT, PosturePolicy.fromSensors(180f, null))
    }

    @Test
    fun `effects never set a session policy`() {
        assertEquals(
            PostureEffect.PAUSE_IF_PLAYING,
            PosturePolicy.effect(
                DevicePosture.CLOSED, DevicePosture.BOOK,
                true, false, true, false, true,
            ),
        )
        assertEquals(
            PostureEffect.NONE,
            PosturePolicy.effect(
                DevicePosture.FLAT, DevicePosture.BOOK,
                true, false, true, false, true,
            ),
        )
        assertEquals(
            PostureEffect.SHOW_YIELD_CHIP,
            PosturePolicy.effect(
                DevicePosture.FLAT, DevicePosture.BOOK,
                true, false, true, true, true,
            ),
        )
        assertEquals(
            PostureEffect.NONE,
            PosturePolicy.effect(
                DevicePosture.FLAT, DevicePosture.BOOK,
                true, true, true, true, true,
            ),
        )
        assertEquals(
            PostureEffect.HIDE_YIELD_CHIP,
            PosturePolicy.effect(
                DevicePosture.BOOK, DevicePosture.FLAT,
                true, false, true, true, true,
            ),
        )
        assertEquals(
            PostureEffect.NONE,
            PosturePolicy.effect(
                DevicePosture.CLOSED, DevicePosture.BOOK,
                true, false, true, false, false,
            ),
        )
    }
}
