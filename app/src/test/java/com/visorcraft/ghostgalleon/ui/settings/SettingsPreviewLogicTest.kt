package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
