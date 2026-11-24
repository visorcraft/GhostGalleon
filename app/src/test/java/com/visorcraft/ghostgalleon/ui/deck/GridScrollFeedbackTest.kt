package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridScrollFeedbackTest {

    @Test
    fun `L1 and R1 classify page flips vs clamped boundary bumps accurately`() {
        // 24 slots, 10 per page -> pages [0..9], [10..19], [20..23]
        assertEquals(
            GridScrollFeedback.Cue.PageFlipped(+1),
            GridScrollFeedback.classify(Action.PAGE_NEXT, fromSlot = 3, toSlot = 13, cellsPerPage = 10, slotCount = 24),
        )
        assertEquals(
            GridScrollFeedback.Cue.PageFlipped(-1),
            GridScrollFeedback.classify(Action.PAGE_PREV, fromSlot = 13, toSlot = 3, cellsPerPage = 10, slotCount = 24),
        )
        // Already at first slot -> L1 produces start edge bump.
        assertEquals(
            GridScrollFeedback.Cue.EdgeBump(-1),
            GridScrollFeedback.classify(Action.PAGE_PREV, fromSlot = 0, toSlot = 0, cellsPerPage = 10, slotCount = 24),
        )
        // Already at last slot -> R1 produces end edge bump.
        assertEquals(
            GridScrollFeedback.Cue.EdgeBump(+1),
            GridScrollFeedback.classify(Action.PAGE_NEXT, fromSlot = 23, toSlot = 23, cellsPerPage = 10, slotCount = 24),
        )
        // Partial step inside page 0 (slot 4 -> slot 0 via L1) stays quiet.
        assertEquals(
            GridScrollFeedback.Cue.None,
            GridScrollFeedback.classify(Action.PAGE_PREV, fromSlot = 4, toSlot = 0, cellsPerPage = 10, slotCount = 24),
        )
    }

    @Test
    fun `directional nav detects both cross-page flips and edge clamps`() {
        // Normal intra-page move is quiet.
        assertEquals(
            GridScrollFeedback.Cue.None,
            GridScrollFeedback.classify(Action.NAV_RIGHT, fromSlot = 2, toSlot = 3, cellsPerPage = 10, slotCount = 24),
        )
        // Crossing from slot 9 to slot 10 triggers PageFlipped(+1).
        assertEquals(
            GridScrollFeedback.Cue.PageFlipped(+1),
            GridScrollFeedback.classify(Action.NAV_RIGHT, fromSlot = 9, toSlot = 10, cellsPerPage = 10, slotCount = 24),
        )
        // Clamped LEFT at 0 or UP at top row triggers start bump.
        assertEquals(
            GridScrollFeedback.Cue.EdgeBump(-1),
            GridScrollFeedback.classify(Action.NAV_LEFT, fromSlot = 0, toSlot = 0, cellsPerPage = 10, slotCount = 24),
        )
        assertEquals(
            GridScrollFeedback.Cue.EdgeBump(-1),
            GridScrollFeedback.classify(Action.NAV_UP, fromSlot = 2, toSlot = 2, cellsPerPage = 10, slotCount = 24),
        )
        // Clamped RIGHT at final slot triggers end bump.
        assertEquals(
            GridScrollFeedback.Cue.EdgeBump(+1),
            GridScrollFeedback.classify(Action.NAV_RIGHT, fromSlot = 23, toSlot = 23, cellsPerPage = 10, slotCount = 24),
        )
    }

    @Test
    fun `fadingEdgeLengthDp scales smoothly between Sugar and Odin viewports`() {
        val sugar = GridScrollFeedback.fadingEdgeLengthDp(viewportHeightPx = 640, density = 2.0f) // 320dp
        val odin = GridScrollFeedback.fadingEdgeLengthDp(viewportHeightPx = 1100, density = 2.0f) // 550dp
        val tiny = GridScrollFeedback.fadingEdgeLengthDp(viewportHeightPx = 100, density = 2.0f)

        assertTrue("sugar fade $sugar in [12, 22]", sugar in 12..22)
        assertTrue("odin fade $odin in [12, 22]", odin in 12..22)
        assertTrue("odin fade ($odin) >= sugar fade ($sugar)", odin >= sugar)
        assertEquals(GridScrollFeedback.MIN_FADING_EDGE_DP, tiny)
    }
}
