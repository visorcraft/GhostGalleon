package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchGuardLogicTest {

    @Test
    fun `classifySlotKey separates folders, ROMs, and app packages`() {
        assertEquals(LaunchGuardLogic.LaunchTargetKind.FOLDER_NOOP, LaunchGuardLogic.classifySlotKey("folder:rpgs"))
        assertEquals(LaunchGuardLogic.LaunchTargetKind.ROM, LaunchGuardLogic.classifySlotKey("rom:snes/chrono"))
        assertEquals(LaunchGuardLogic.LaunchTargetKind.APP, LaunchGuardLogic.classifySlotKey("com.retroarch"))
    }

    @Test
    fun `resolveLaunchDisplayId respects override, topology, soft fallback, and detached guards`() {
        // Dual Sugar: primary=1, topology launch=0, currently on 1 -> launch on 0.
        assertEquals(
            0,
            LaunchGuardLogic.resolveLaunchDisplayId(
                overrideDisplayId = null,
                topologyLaunchDisplayId = 0,
                primaryDisplayId = 1,
                currentDisplayId = 1,
                availableDisplayIds = listOf(0, 1),
            ),
        )
        // Stage-plot override pointing to current display -> null (launch locally).
        assertNull(
            LaunchGuardLogic.resolveLaunchDisplayId(
                overrideDisplayId = 1,
                topologyLaunchDisplayId = 0,
                primaryDisplayId = 1,
                currentDisplayId = 1,
                availableDisplayIds = listOf(0, 1),
            ),
        )
        // Single-screen Odin 2 Portal -> null.
        assertNull(
            LaunchGuardLogic.resolveLaunchDisplayId(
                overrideDisplayId = null,
                topologyLaunchDisplayId = 0,
                primaryDisplayId = 0,
                currentDisplayId = 0,
                availableDisplayIds = listOf(0),
            ),
        )
        // Detached stale topology display ID -> null instead of crashing ActivityOptions.
        assertNull(
            LaunchGuardLogic.resolveLaunchDisplayId(
                overrideDisplayId = null,
                topologyLaunchDisplayId = 2,
                primaryDisplayId = 0,
                currentDisplayId = 0,
                availableDisplayIds = listOf(0),
            ),
        )
        // Soft fallback without topology chooses first display != primary.
        assertEquals(
            1,
            LaunchGuardLogic.resolveLaunchDisplayId(
                overrideDisplayId = null,
                topologyLaunchDisplayId = null,
                primaryDisplayId = 0,
                currentDisplayId = 0,
                availableDisplayIds = listOf(0, 1),
            ),
        )
        // Unknown current display id still honors valid topology display.
        assertEquals(
            1,
            LaunchGuardLogic.resolveLaunchDisplayId(
                overrideDisplayId = null,
                topologyLaunchDisplayId = 1,
                primaryDisplayId = 0,
                currentDisplayId = null,
                availableDisplayIds = listOf(0, 1),
            ),
        )
    }
}
