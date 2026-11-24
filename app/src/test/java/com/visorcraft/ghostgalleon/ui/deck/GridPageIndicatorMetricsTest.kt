package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPageIndicatorMetricsTest {

    @Test
    fun `stripState tracks page index and prev-next edge flags accurately`() {
        val first = GridPageIndicatorMetrics.stripState(selectedSlot = 2, slotCount = 24, cellsPerPage = 10)
        assertEquals(0, first.currentPage)
        assertEquals(3, first.totalPages)
        assertFalse(first.canPagePrev)
        assertTrue(first.canPageNext)

        val middle = GridPageIndicatorMetrics.stripState(selectedSlot = 12, slotCount = 24, cellsPerPage = 10)
        assertEquals(1, middle.currentPage)
        assertTrue(middle.canPagePrev)
        assertTrue(middle.canPageNext)

        val last = GridPageIndicatorMetrics.stripState(selectedSlot = 23, slotCount = 24, cellsPerPage = 10)
        assertEquals(2, last.currentPage)
        assertTrue(last.canPagePrev)
        assertFalse(last.canPageNext)

        val single = GridPageIndicatorMetrics.stripState(selectedSlot = 3, slotCount = 8, cellsPerPage = 10)
        assertEquals(0, single.currentPage)
        assertEquals(1, single.totalPages)
        assertFalse(single.canPagePrev)
        assertFalse(single.canPageNext)
    }

    @Test
    fun `targetSlotForPage preserves in-page offset and clamps partial tail pages`() {
        // 24 slots, 10 per page -> pages [0..9], [10..19], [20..23]
        // From slot 3 (offset 3 on page 0) -> jump to page 1 lands on 13.
        assertEquals(13, GridPageIndicatorMetrics.targetSlotForPage(1, currentSlot = 3, slotCount = 24, cellsPerPage = 10))
        // Jump to page 2 (partial: 20..23) -> offset 3 lands on 23.
        assertEquals(23, GridPageIndicatorMetrics.targetSlotForPage(2, currentSlot = 3, slotCount = 24, cellsPerPage = 10))
        // From slot 8 (offset 8) -> page 2 clamps cleanly to last slot 23.
        assertEquals(23, GridPageIndicatorMetrics.targetSlotForPage(2, currentSlot = 8, slotCount = 24, cellsPerPage = 10))
        // Tapping current page stays on exact slot.
        assertEquals(8, GridPageIndicatorMetrics.targetSlotForPage(0, currentSlot = 8, slotCount = 24, cellsPerPage = 10))
        // Out-of-range page clamps safely.
        assertEquals(8, GridPageIndicatorMetrics.targetSlotForPage(-5, currentSlot = 8, slotCount = 24, cellsPerPage = 10))
        assertEquals(23, GridPageIndicatorMetrics.targetSlotForPage(99, currentSlot = 8, slotCount = 24, cellsPerPage = 10))
    }

    @Test
    fun `stepPageSlot moves cleanly forward and backward and clamps at ends`() {
        assertEquals(14, GridPageIndicatorMetrics.stepPageSlot(+1, currentSlot = 4, slotCount = 30, cellsPerPage = 10))
        assertEquals(4, GridPageIndicatorMetrics.stepPageSlot(-1, currentSlot = 14, slotCount = 30, cellsPerPage = 10))
        assertEquals(4, GridPageIndicatorMetrics.stepPageSlot(-1, currentSlot = 4, slotCount = 30, cellsPerPage = 10))
        assertEquals(24, GridPageIndicatorMetrics.stepPageSlot(+1, currentSlot = 24, slotCount = 30, cellsPerPage = 10))
    }

    @Test
    fun `pill width and ARGB interpolation behave smoothly across endpoints`() {
        assertEquals(12, GridPageIndicatorMetrics.interpolateWidthPx(12, 40, 0f))
        assertEquals(26, GridPageIndicatorMetrics.interpolateWidthPx(12, 40, 0.5f))
        assertEquals(40, GridPageIndicatorMetrics.interpolateWidthPx(12, 40, 1f))

        val start = GridPageIndicatorMetrics.INACTIVE_DOT_COLOR
        val end = 0xFF00A8A0.toInt()
        assertEquals(start, GridPageIndicatorMetrics.interpolateArgb(start, end, 0f))
        assertEquals(end, GridPageIndicatorMetrics.interpolateArgb(start, end, 1f))
        val mid = GridPageIndicatorMetrics.interpolateArgb(start, end, 0.5f)
        val midAlpha = (mid ushr 24) and 0xFF
        assertTrue("midpoint alpha $midAlpha expected between 0x52 and 0xFF", midAlpha in 0x60..0xF0)
    }

    @Test
    fun `activePillColor meets WCAG AA over black across all built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val pill = GridPageIndicatorMetrics.activePillColor(tokens.accentColor)
            val ratio = ThemePack.contrastRatio(pill, 0xFF000000.toInt())
            assertTrue("theme=${tokens.id} ratio=$ratio < 4.5", ratio >= ThemePack.MIN_TEXT_CONTRAST)
        }
    }
}
