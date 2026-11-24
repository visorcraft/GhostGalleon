package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.input.SeatAnchor
import com.visorcraft.ghostgalleon.input.SecondSeatPolicy
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPreviewLogicTest {

    @Test
    fun `miniGridMetrics clamps inputs and scales icon and card proportions monotonically`() {
        val min = SettingsPreviewLogic.miniGridMetrics(
            columns = 1,
            iconSizeDp = 16,
            cardSizeDp = 50,
            showLabels = false,
            gridDirection = "vertical",
        )
        assertEquals(SettingsPreviewLogic.MIN_COLUMNS, min.columns)
        assertEquals(2, min.rows)
        assertFalse(min.showLabels)
        assertFalse(min.horizontal)

        val mid = SettingsPreviewLogic.miniGridMetrics(
            columns = 5,
            iconSizeDp = 88,
            cardSizeDp = 220,
            showLabels = true,
            gridDirection = "horizontal",
        )
        assertEquals(5, mid.columns)
        assertTrue(mid.showLabels)
        assertTrue(mid.horizontal)

        val max = SettingsPreviewLogic.miniGridMetrics(
            columns = 12,
            iconSizeDp = 200,
            cardSizeDp = 500,
            showLabels = true,
            gridDirection = "HORIZONTAL",
        )
        assertEquals(SettingsPreviewLogic.MAX_COLUMNS, max.columns)
        assertTrue(max.horizontal)

        assertTrue("iconScale must increase monotonically", min.iconScale < mid.iconScale && mid.iconScale < max.iconScale)
        assertTrue("cardRatio must increase monotonically", min.cardRatio < mid.cardRatio && mid.cardRatio < max.cardRatio)
    }

    @Test
    fun `computeMiniGridCells produces non-overlapping in-bounds tiles with one focused`() {
        val cells = SettingsPreviewLogic.computeMiniGridCells(
            width = 300f,
            height = 120f,
            columns = 5,
            rows = 2,
            gapPx = 6f,
            focusedIndex = 1,
        )
        assertEquals(10, cells.size)
        assertEquals(1, cells.count { it.focused })
        assertTrue(cells[1].focused)
        for (cell in cells) {
            assertTrue(cell.left >= 0f && cell.right <= 300.01f)
            assertTrue(cell.top >= 0f && cell.bottom <= 120.01f)
            assertTrue(cell.right > cell.left)
            assertTrue(cell.bottom > cell.top)
        }
        // Adjacent cells in row 0 do not overlap.
        assertTrue(cells[0].right <= cells[1].left + 0.01f)
        // Row 0 sits above row 1.
        assertTrue(cells[0].bottom <= cells[5].top + 0.01f)

        assertTrue(
            SettingsPreviewLogic.computeMiniGridCells(0f, 100f, 5, 2, 4f).isEmpty(),
        )
    }

    @Test
    fun `scaledCornerRadiusPx reflects theme cardRadiusDp differences`() {
        val ghostR = SettingsPreviewLogic.scaledCornerRadiusPx(ThemePack.GHOST.cardRadiusDp, density = 2f)
        val oledR = SettingsPreviewLogic.scaledCornerRadiusPx(ThemePack.OLED.cardRadiusDp, density = 2f)
        assertTrue("Ghost ($ghostR) should be rounder than OLED ($oledR)", ghostR > oledR)
        assertTrue(oledR >= 4f)
    }

    @Test
    fun `SecondSeatPreviewLogic maps default anchors into padded viewport and classifies glyphs`() {
        val dots = SecondSeatPreviewLogic.layoutDots(
            anchors = SecondSeatPolicy.DEFAULT_ANCHORS,
            widthPx = 320f,
            heightPx = 180f,
            padPx = 16f,
        )
        assertEquals(SecondSeatPolicy.DEFAULT_ANCHORS.size, dots.size)
        assertEquals(4, dots.count { it.isDpad })
        assertEquals("\u25B2", SecondSeatPreviewLogic.glyphForAnchor("up"))
        assertEquals("A", SecondSeatPreviewLogic.glyphForAnchor("a"))
        assertEquals("ST", SecondSeatPreviewLogic.glyphForAnchor("start"))
        assertEquals("L1", SecondSeatPreviewLogic.glyphForAnchor("l1"))
        for (dot in dots) {
            assertTrue("xPx=${dot.xPx} out of padded bounds", dot.xPx in 16f..304f)
            assertTrue("yPx=${dot.yPx} out of padded bounds", dot.yPx in 16f..164f)
        }

        // Clamps out-of-range and NaN coordinates cleanly.
        val clamped = SecondSeatPreviewLogic.layoutDots(
            anchors = listOf(SeatAnchor("a", -0.5f, 2f), SeatAnchor("b", Float.NaN, Float.POSITIVE_INFINITY)),
            widthPx = 200f,
            heightPx = 100f,
            padPx = 10f,
        )
        assertEquals(10f, clamped[0].xPx, 0.01f)
        assertEquals(90f, clamped[0].yPx, 0.01f)
        assertEquals(100f, clamped[1].xPx, 0.01f)
        assertEquals(50f, clamped[1].yPx, 0.01f)

        assertTrue(SecondSeatPreviewLogic.layoutDots(SecondSeatPolicy.DEFAULT_ANCHORS, 0f, 100f, 10f).isEmpty())
    }

    @Test
    fun `MiniGridCellCache reuses precomputed cells across unchanged frames and invalidates on resize or column change`() {
        val cache = MiniGridCellCache()
        val first = cache.ensure(width = 300f, height = 120f, columns = 5, rows = 2, gapPx = 5f, focusedIndex = 1)
        assertEquals(10, first.size)
        assertEquals(1, cache.recomputeCount)

        // Repeated frames (e.g. 120 Hz icon/card slider drags) reuse the exact cached list with zero recomputation.
        repeat(60) {
            val hit = cache.ensure(width = 300f, height = 120f, columns = 5, rows = 2, gapPx = 5f, focusedIndex = 1)
            assertSame(first, hit)
        }
        assertEquals(1, cache.recomputeCount)

        // Changing columns recomputes cleanly.
        val nextCols = cache.ensure(width = 300f, height = 120f, columns = 6, rows = 2, gapPx = 5f, focusedIndex = 1)
        assertNotSame(first, nextCols)
        assertEquals(12, nextCols.size)
        assertEquals(2, cache.recomputeCount)

        // Resizing viewport recomputes cleanly.
        val resized = cache.ensure(width = 360f, height = 120f, columns = 6, rows = 2, gapPx = 5f, focusedIndex = 1)
        assertNotSame(nextCols, resized)
        assertEquals(3, cache.recomputeCount)
        assertTrue(resized.last().right > nextCols.last().right)
    }

    @Test
    fun `SecondSeatDotCache reuses precomputed dots across redraws and recomputes on anchor or viewport changes`() {
        val cache = SecondSeatDotCache()
        val initial = SecondSeatPolicy.DEFAULT_ANCHORS
        val first = cache.ensure(initial, widthPx = 320f, heightPx = 180f, padPx = 16f)
        assertEquals(initial.size, first.size)
        assertEquals(1, cache.recomputeCount)

        // Same reference or structurally equal anchor list hits cache (zero allocations on highlight/redraw).
        assertSame(first, cache.ensure(initial, widthPx = 320f, heightPx = 180f, padPx = 16f))
        assertSame(first, cache.ensure(initial.toList(), widthPx = 320f, heightPx = 180f, padPx = 16f))
        assertEquals(1, cache.recomputeCount)

        // Mutating an anchor coordinate invalidates and moves that dot.
        val moved = initial.map { if (it.id == "a") SeatAnchor("a", 0.1f, 0.2f) else it }
        val afterMove = cache.ensure(moved, widthPx = 320f, heightPx = 180f, padPx = 16f)
        assertNotSame(first, afterMove)
        assertEquals(2, cache.recomputeCount)
        val dotA = afterMove.first { it.id == "a" }
        assertEquals(16f + 0.1f * 288f, dotA.xPx, 0.01f)
        assertEquals(16f + 0.2f * 148f, dotA.yPx, 0.01f)

        // Verify DPAD classification handles whitespace and uppercase cleanly.
        assertTrue(SecondSeatPreviewLogic.isDpadAnchor(" UP "))
        assertTrue(SecondSeatPreviewLogic.isDpadAnchor("left"))
        assertFalse(SecondSeatPreviewLogic.isDpadAnchor("a"))
        assertFalse(SecondSeatPreviewLogic.isDpadAnchor("start"))
    }
}
