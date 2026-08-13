package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StagePlotTest {

    @Test
    fun `resolve prefers rom then pack then package then player`() {
        val rom = StagePlot(SessionPolicy.KEEP_COMPANION, LaunchFace.INTERACTIVE)
        val pack = StagePlot(SessionPolicy.YIELD_BOTH, LaunchFace.COMPANION)
        assertEquals(rom, StagePlots.resolve(rom, pack, true, "melondualds"))
        assertEquals(pack, StagePlots.resolve(null, pack, true, "melondualds"))
        val y = StagePlots.resolve(null, null, true, "ra-snes9x")
        assertEquals(SessionPolicy.YIELD_BOTH, y.policy)
        assertEquals(LaunchFace.AUTO, y.launchFace)
        val p = StagePlots.resolve(null, null, false, "melondualds")
        assertEquals(SessionPolicy.YIELD_BOTH, p.policy)
        val k = StagePlots.resolve(null, null, false, "ra-snes9x")
        assertEquals(SessionPolicy.KEEP_COMPANION, k.policy)
    }

    @Test
    fun `yield ignores launch face`() {
        assertEquals(
            0,
            StagePlots.launchDisplayId(LaunchFace.INTERACTIVE, SessionPolicy.YIELD_BOTH, 1, 2, 0),
        )
        assertEquals(
            1,
            StagePlots.launchDisplayId(LaunchFace.INTERACTIVE, SessionPolicy.KEEP_COMPANION, 1, 2, 0),
        )
        assertEquals(
            2,
            StagePlots.launchDisplayId(LaunchFace.COMPANION, SessionPolicy.KEEP_COMPANION, 1, 2, 0),
        )
        assertEquals(
            0,
            StagePlots.launchDisplayId(LaunchFace.AUTO, SessionPolicy.KEEP_COMPANION, 1, 2, 0),
        )
        assertEquals(
            0,
            StagePlots.launchDisplayId(LaunchFace.OTHER, SessionPolicy.KEEP_COMPANION, 1, 2, 0),
        )
        assertNull(
            StagePlots.launchDisplayId(LaunchFace.AUTO, SessionPolicy.KEEP_COMPANION, null, null, null),
        )
    }

    @Test
    fun `confirm when overriding built-in policy`() {
        assertEquals(
            PlotConfirm.KEEP_ON_YIELD_PLAYER,
            StagePlots.confirmFor(SessionPolicy.YIELD_BOTH, SessionPolicy.KEEP_COMPANION),
        )
        assertEquals(
            PlotConfirm.YIELD_ON_KEEP_PLAYER,
            StagePlots.confirmFor(SessionPolicy.KEEP_COMPANION, SessionPolicy.YIELD_BOTH),
        )
        assertEquals(
            PlotConfirm.NONE,
            StagePlots.confirmFor(SessionPolicy.YIELD_BOTH, SessionPolicy.YIELD_BOTH),
        )
        assertEquals(
            PlotConfirm.NONE,
            StagePlots.confirmFor(SessionPolicy.YIELD_BOTH, null),
        )
    }
}
